package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.repository.OnCallRoleRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import org.springframework.stereotype.Service;

/** role_explanation (FR-011): stored approved role definition, exact/alias match only. */
@Service
public class RoleExplanationToolService {

    private final OnCallRoleRepository onCallRoleRepository;
    private final AuthorizationService authorizationService;
    private final String requiredScope;

    public RoleExplanationToolService(
            OnCallRoleRepository onCallRoleRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.onCallRoleRepository = onCallRoleRepository;
        this.authorizationService = authorizationService;
        this.requiredScope = chatbotConfigLoader.config().toolsById().get("role_explanation").authorizationScope();
    }

    public ToolResult<OnCallRole> getRoleExplanation(AuthenticatedSubject subject, String roleCode) {
        authorizationService.authorize(subject, requiredScope);
        return onCallRoleRepository
                .findByCodeIgnoreCaseAndActiveTrue(roleCode)
                .<ToolResult<OnCallRole>>map(ToolResult::found)
                .orElseGet(ToolResult::noMatch);
    }
}
