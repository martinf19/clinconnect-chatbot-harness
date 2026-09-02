package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.ContactMethod;
import com.clinconnect.chatbot.domain.repository.ContactMethodRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * get_contact_info (FR-006). Always returns the provider's full active
 * contact list; a specific requested {@code contact_type} not being present
 * is a formatting concern ("reported as not listed"), not a NO_MATCH for
 * the whole request — only a provider with zero active contacts is
 * NO_MATCH. No fallback provider/contact is invented.
 */
@Service
public class ContactInfoToolService {

    private final ContactMethodRepository contactMethodRepository;
    private final AuthorizationService authorizationService;
    private final String requiredScope;

    public ContactInfoToolService(
            ContactMethodRepository contactMethodRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.contactMethodRepository = contactMethodRepository;
        this.authorizationService = authorizationService;
        this.requiredScope = chatbotConfigLoader.config().toolsById().get("get_contact_info").authorizationScope();
    }

    public ToolResult<List<ContactMethod>> getContactInfo(AuthenticatedSubject subject, String providerId) {
        authorizationService.authorize(subject, requiredScope);
        List<ContactMethod> contacts = contactMethodRepository.findByProviderIdAndActiveTrue(providerId);
        if (contacts.isEmpty()) {
            return ToolResult.noMatch();
        }
        return ToolResult.found(contacts);
    }
}
