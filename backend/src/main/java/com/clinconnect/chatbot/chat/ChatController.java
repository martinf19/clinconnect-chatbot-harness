package com.clinconnect.chatbot.chat;

import com.clinconnect.chatbot.correlation.CorrelationIdFilter;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.DevAuthenticationProvider;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/08-API-CONTRACTS.md {@code POST /api/v1/chat/messages}. Implements
 * docs/09-SECURITY.md's mandatory ordering only up through "authenticate
 * caller" here; session validation/ownership/idempotency/interpretation/
 * canonicalization/authorization/execution all happen inside
 * {@link ChatOrchestrationService}.
 */
@RestController
public class ChatController {

    private final DevAuthenticationProvider authenticationProvider;
    private final ChatOrchestrationService orchestrationService;

    public ChatController(DevAuthenticationProvider authenticationProvider, ChatOrchestrationService orchestrationService) {
        this.authenticationProvider = authenticationProvider;
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/api/v1/chat/messages")
    public ChatMessageResponse postMessage(@RequestBody ChatMessageRequest request) {
        AuthenticatedSubject subject = authenticationProvider.resolveCurrentSubject();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return orchestrationService.handleMessage(subject, request, correlationId);
    }

    @ExceptionHandler(SessionOwnershipException.class)
    public ResponseEntity<Void> handleSessionOwnershipException() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(DuplicateMessageConflictException.class)
    public ResponseEntity<Void> handleDuplicateMessageConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
