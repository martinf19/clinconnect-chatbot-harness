package com.clinconnect.chatbot.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationIdProviderTest {

    @Test
    void generatesDistinctValidUuids() {
        CorrelationIdProvider provider = new CorrelationIdProvider();

        String first = provider.newCorrelationId();
        String second = provider.newCorrelationId();

        assertThat(first).isNotEqualTo(second);
        assertThat(UUID.fromString(first)).isNotNull();
        assertThat(UUID.fromString(second)).isNotNull();
    }
}
