package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.Department;
import com.clinconnect.chatbot.domain.repository.DepartmentRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import org.springframework.stereotype.Service;

/** get_department_info (FR-007): narrow approved department fields only. */
@Service
public class DepartmentInfoToolService {

    private final DepartmentRepository departmentRepository;
    private final AuthorizationService authorizationService;
    private final String requiredScope;

    public DepartmentInfoToolService(
            DepartmentRepository departmentRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.departmentRepository = departmentRepository;
        this.authorizationService = authorizationService;
        this.requiredScope =
                chatbotConfigLoader.config().toolsById().get("get_department_info").authorizationScope();
    }

    public ToolResult<Department> getDepartmentInfo(
            AuthenticatedSubject subject, String locationId, String specialtyId) {
        authorizationService.authorize(subject, requiredScope);
        return departmentRepository
                .findByLocationIdAndSpecialtyIdAndActiveTrue(locationId, specialtyId)
                .<ToolResult<Department>>map(ToolResult::found)
                .orElseGet(ToolResult::noMatch);
    }
}
