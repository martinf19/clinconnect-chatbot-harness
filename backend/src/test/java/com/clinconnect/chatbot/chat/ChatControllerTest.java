package com.clinconnect.chatbot.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP-level contract for POST /api/v1/chat/messages (docs/08-API-CONTRACTS.md). */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatOrchestrationService orchestrationService;

    @Test
    void successfulMessageReturns200WithSnakeCaseBody() throws Exception {
        when(orchestrationService.handleMessage(any(), any(), any()))
                .thenReturn(ChatMessageResponse.answer("session-1", "Locations: Oakland, Antioch", "corr-1"));

        mockMvc.perform(post("/api/v1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"session_id": null, "client_message_id": "11111111-1111-4111-8111-111111111111", "message": "What locations can I search?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value("session-1"))
                .andExpect(jsonPath("$.status").value("ANSWER"))
                .andExpect(jsonPath("$.answer_text").value("Locations: Oakland, Antioch"));
    }

    @Test
    void sessionOwnershipMismatchReturns403WithNoBody() throws Exception {
        when(orchestrationService.handleMessage(any(), any(), any())).thenThrow(new SessionOwnershipException());

        mockMvc.perform(post("/api/v1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"session_id": "someone-elses-session", "client_message_id": "11111111-1111-4111-8111-111111111111", "message": "hi"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateMessageConflictReturns409() throws Exception {
        when(orchestrationService.handleMessage(any(), any(), any())).thenThrow(new DuplicateMessageConflictException());

        mockMvc.perform(post("/api/v1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"session_id": null, "client_message_id": "11111111-1111-4111-8111-111111111111", "message": "hi"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void responseCarriesTheCorrelationIdHeader() throws Exception {
        when(orchestrationService.handleMessage(any(), any(), any()))
                .thenReturn(ChatMessageResponse.unsupported("session-1", "corr-1"));

        mockMvc.perform(post("/api/v1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"session_id": null, "client_message_id": "22222222-2222-4222-8222-222222222222", "message": "hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
