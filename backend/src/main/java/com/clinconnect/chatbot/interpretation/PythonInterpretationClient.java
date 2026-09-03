package com.clinconnect.chatbot.interpretation;

import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the Python AI service's {@code POST /interpret} (Phase 2). Python
 * owns language interpretation only (CLAUDE.md Ownership) — this client
 * never lets Python choose a tool or produce a canonical ID; it just
 * forwards the raw response for Spring's orchestrator to canonicalize and
 * validate like any other untrusted input.
 *
 * <p><b>Phase 7 POC hardening — bounded transport retry:</b> a single retry,
 * after a short fixed delay, applies only to a genuine transport-level
 * failure: an {@link IOException} anywhere in the exception's cause chain
 * (connection refused/reset, timeout, truncated response). That is
 * deliberately broader than checking for {@code ResourceAccessException}
 * alone — verified against a real local TCP peer in {@code
 * PythonInterpretationClientTest} (not a mock), which found that a
 * connection reset partway through reading the response body surfaces as a
 * plain {@link RestClientException} wrapping a {@code SocketException}, not
 * always {@code ResourceAccessException}. A well-formed non-2xx response
 * from ai-service (e.g. its own 502/503 for a failed interpretation) is
 * deliberately <em>not</em> retried — no {@code IOException} is involved,
 * ai-service already applied its own retry/fail-closed handling for that
 * case (see {@code ai_service.interpretation.service.interpret}), and
 * blindly retrying a structural failure it already reported would just
 * repeat the same outcome. Exhausting the retry still fails closed exactly
 * as before: {@link InterpretationUnavailableException}, no tool ever
 * executes.
 */
@Component
public class PythonInterpretationClient {

    private static final Logger log = LoggerFactory.getLogger(PythonInterpretationClient.class);
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final Duration retryDelay;

    public PythonInterpretationClient(
            JsonMapper objectMapper,
            @Value("${clinconnect.ai-service.base-url}") String baseUrl,
            @Value("${clinconnect.ai-service.timeout-seconds}") long timeoutSeconds,
            @Value("${clinconnect.ai-service.retry-delay-millis:300}") long retryDelayMillis) {
        int timeoutMillis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.retryDelay = Duration.ofMillis(retryDelayMillis);
        // Uses the app's own auto-configured ObjectMapper (spring.jackson
        // .property-naming-strategy: SNAKE_CASE) so this client's DTOs serialize with the
        // same snake_case contract ai-service expects/returns (docs/08-API-CONTRACTS.md),
        // since a manually-built RestClient does not inherit Boot's message converters.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, new JacksonJsonHttpMessageConverter(objectMapper)))
                .build();
    }

    public InterpretResponse interpret(InterpretRequest request, String correlationId) {
        RestClientException lastTransportFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                InterpretResponse response = restClient.post()
                        .uri("/interpret")
                        .header("X-Correlation-Id", correlationId)
                        .body(request)
                        .retrieve()
                        .body(InterpretResponse.class);
                if (response == null) {
                    throw new InterpretationUnavailableException("AI service returned an empty body", null);
                }
                return response;
            } catch (RestClientException e) {
                if (!isTransportFailure(e)) {
                    throw new InterpretationUnavailableException("AI service call failed: " + e.getMessage(), e);
                }
                lastTransportFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn(
                            "AI service transport failure on attempt {}/{}, retrying once: {}",
                            attempt, MAX_ATTEMPTS, e.getMessage());
                    sleep(retryDelay);
                }
            }
        }
        throw new InterpretationUnavailableException(
                "AI service call failed after retry: " + lastTransportFailure.getMessage(), lastTransportFailure);
    }

    /** True when an {@link IOException} appears anywhere in the cause chain — see class Javadoc. */
    private static boolean isTransportFailure(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterpretationUnavailableException("Interrupted while retrying AI service call", e);
        }
    }
}
