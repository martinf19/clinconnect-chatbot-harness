package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.Specialty;
import com.clinconnect.chatbot.domain.repository.LocationSpecialtyRepository;
import com.clinconnect.chatbot.domain.repository.SpecialtyRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

/** get_specialties (FR-003): active specialties offered at one canonical location. */
@Service
public class SpecialtyToolService {

    private final LocationSpecialtyRepository locationSpecialtyRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AuthorizationService authorizationService;
    private final String requiredScope;

    public SpecialtyToolService(
            LocationSpecialtyRepository locationSpecialtyRepository,
            SpecialtyRepository specialtyRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.locationSpecialtyRepository = locationSpecialtyRepository;
        this.specialtyRepository = specialtyRepository;
        this.authorizationService = authorizationService;
        this.requiredScope = chatbotConfigLoader.config().toolsById().get("get_specialties").authorizationScope();
    }

    public ToolResult<List<Specialty>> getSpecialties(AuthenticatedSubject subject, String locationId) {
        authorizationService.authorize(subject, requiredScope);
        List<Specialty> specialties = locationSpecialtyRepository.findByLocationIdAndActiveTrue(locationId).stream()
                .map(ls -> specialtyRepository.findById(ls.getSpecialtyId()))
                .flatMap(java.util.Optional::stream)
                .filter(Specialty::isActive)
                .toList();
        return ToolResult.found(specialties);
    }
}
