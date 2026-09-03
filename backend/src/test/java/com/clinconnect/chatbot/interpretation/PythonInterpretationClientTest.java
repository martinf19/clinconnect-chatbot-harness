package com.clinconnect.chatbot.interpretation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 7 POC hardening: verifies {@link PythonInterpretationClient}'s single-bounded-retry
 * policy (see its own Javadoc) against a real local TCP peer ({@link FlakyHttpServer}) rather
 * than mocks, so the actual Spring HTTP client stack's exception classification —
 * {@code ResourceAccessException} for a genuine transport failure vs. a well-formed non-2xx
 * response — is exercised for real, not assumed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PythonInterpretationClientTest {

    private static final long TIMEOUT_SECONDS = 2;
    private static final long RETRY_DELAY_MILLIS = 50;
    private static final String VALID_BODY = "{\"interpretation_status\": \"UNSUPPORTED\"}";

    @Autowired
    private JsonMapper jsonMapper;

    private PythonInterpretationClient client(FlakyHttpServer server) {
        return new PythonInterpretationClient(jsonMapper, server.baseUrl(), TIMEOUT_SECONDS, RETRY_DELAY_MILLIS);
    }

    private static InterpretRequest request() {
        return new InterpretRequest("hi", null, null);
    }

    @Test
    void succeedsOnFirstAttemptWithNoRetryNeeded() throws IOException {
        try (FlakyHttpServer server = new FlakyHttpServer(0, 0, VALID_BODY)) {
            InterpretResponse response = client(server).interpret(request(), "corr-1");

            assertThat(response.interpretationStatus()).isEqualTo(InterpretationStatus.UNSUPPORTED);
            assertThat(server.requestCount()).isEqualTo(1);
        }
    }

    @Test
    void retriesOnceAfterATransientTransportFailureThenSucceeds() throws IOException {
        // First connection is accepted then abruptly closed with no HTTP response at all
        // (a genuine transport-level failure, not a mock) — the second must succeed.
        try (FlakyHttpServer server = new FlakyHttpServer(1, 0, VALID_BODY)) {
            InterpretResponse response = client(server).interpret(request(), "corr-1");

            assertThat(response.interpretationStatus()).isEqualTo(InterpretationStatus.UNSUPPORTED);
            assertThat(server.requestCount()).isEqualTo(2);
        }
    }

    @Test
    void failsClosedAfterExhaustingTheSingleRetry() throws IOException {
        try (FlakyHttpServer server = new FlakyHttpServer(Integer.MAX_VALUE, 0, VALID_BODY)) {
            assertThatThrownBy(() -> client(server).interpret(request(), "corr-1"))
                    .isInstanceOf(InterpretationUnavailableException.class);
            // Exactly one retry: not zero (no retry happened), not more (exceeded the bound) —
            // this is what makes it a *bounded* retry, not open-ended.
            assertThat(server.requestCount()).isEqualTo(2);
        }
    }

    @Test
    void aWellFormedErrorResponseIsNeverRetried() throws IOException {
        // ai-service's own 503 means it already tried and failed structurally (docs/09:
        // malformed AI output fails closed) — blindly retrying here would just repeat it.
        try (FlakyHttpServer server = new FlakyHttpServer(0, 503, VALID_BODY)) {
            assertThatThrownBy(() -> client(server).interpret(request(), "corr-1"))
                    .isInstanceOf(InterpretationUnavailableException.class);
            assertThat(server.requestCount()).isEqualTo(1);
        }
    }
}
