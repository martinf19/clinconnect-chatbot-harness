package com.clinconnect.chatbot.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises a real (in-JVM) HTTP round trip through actuator health to
 * confirm the correlation-ID filter and management endpoints are actually
 * wired, not just present as unused beans. Only structural response
 * properties are asserted here, not a specific UP/DOWN status.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsCorrelationIdHeaderAndStatusBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\"")))
                .andReturn();

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }
}
