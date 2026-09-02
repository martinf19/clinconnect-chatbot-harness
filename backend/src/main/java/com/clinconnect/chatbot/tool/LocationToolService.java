package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.repository.LocationRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

/** get_locations (FR-002). */
@Service
public class LocationToolService {

    private final LocationRepository locationRepository;
    private final AuthorizationService authorizationService;
    private final String requiredScope;

    public LocationToolService(
            LocationRepository locationRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.locationRepository = locationRepository;
        this.authorizationService = authorizationService;
        this.requiredScope = chatbotConfigLoader.config().toolsById().get("get_locations").authorizationScope();
    }

    public ToolResult<List<Location>> getLocations(AuthenticatedSubject subject) {
        authorizationService.authorize(subject, requiredScope);
        return ToolResult.found(locationRepository.findByActiveTrueOrderByDisplayNameAsc());
    }
}
