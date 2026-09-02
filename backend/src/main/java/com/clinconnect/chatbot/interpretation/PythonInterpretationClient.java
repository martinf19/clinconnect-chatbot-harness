package com.clinconnect.chatbot.interpretation;

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
 */
@Component
public class PythonInterpretationClient {

    private final RestClient restClient;

    public PythonInterpretationClient(
            JsonMapper objectMapper,
            @Value("${clinconnect.ai-service.base-url}") String baseUrl,
            @Value("${clinconnect.ai-service.timeout-seconds}") long timeoutSeconds) {
        int timeoutMillis = (int) java.time.Duration.ofSeconds(timeoutSeconds).toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
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
            throw new InterpretationUnavailableException("AI service call failed: " + e.getMessage(), e);
        }
    }
}
