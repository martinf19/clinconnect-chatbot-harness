package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.ConsultRoutingCategory;
import com.clinconnect.chatbot.domain.model.ConsultRoutingRule;
import com.clinconnect.chatbot.domain.model.DeclaredUrgency;
import com.clinconnect.chatbot.domain.model.TimeContext;
import com.clinconnect.chatbot.domain.repository.ConsultRoutingRuleRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * get_pcconsult_info / triage_consult (FR-008, FR-009) and
 * chart_chat_guidance (FR-010). Both intents map to tool_id
 * "get_pcconsult_info" except chart_chat_guidance, which has its own
 * tool_id but the same argument shape (config/tools.yaml); both query the
 * same consult_routing_rule table, distinguished by category.
 */
@Service
public class ConsultRoutingToolService {

    private final ConsultRoutingRuleRepository consultRoutingRuleRepository;
    private final AuthorizationService authorizationService;
    private final String consultRoutingScope;
    private final String chartChatScope;

    public ConsultRoutingToolService(
            ConsultRoutingRuleRepository consultRoutingRuleRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.consultRoutingRuleRepository = consultRoutingRuleRepository;
        this.authorizationService = authorizationService;
        this.consultRoutingScope =
                chatbotConfigLoader.config().toolsById().get("get_pcconsult_info").authorizationScope();
        this.chartChatScope =
                chatbotConfigLoader.config().toolsById().get("chart_chat_guidance").authorizationScope();
    }

    /** FR-008 (time_context/declared_urgency optional) and FR-009 (declared_urgency required, caller-enforced). */
    public ToolResult<List<ConsultRoutingRule>> getConsultRouting(
            AuthenticatedSubject subject,
            String locationId,
            String specialtyId,
            TimeContext timeContext,
            DeclaredUrgency declaredUrgency) {
        authorizationService.authorize(subject, consultRoutingScope);
        List<ConsultRoutingRule> rules = consultRoutingRuleRepository.findApplicableRules(
                locationId, specialtyId, ConsultRoutingCategory.CONSULT_ROUTING, timeContext, declaredUrgency);
        if (rules.isEmpty()) {
            return ToolResult.noMatch();
        }
        return ToolResult.found(rules);
    }

    public ToolResult<List<ConsultRoutingRule>> getChartChatGuidance(
            AuthenticatedSubject subject, String locationId, String specialtyId, TimeContext timeContext) {
        authorizationService.authorize(subject, chartChatScope);
        List<ConsultRoutingRule> rules = consultRoutingRuleRepository.findApplicableRules(
                locationId, specialtyId, ConsultRoutingCategory.CHART_CHAT_GUIDANCE, timeContext, null);
        if (rules.isEmpty()) {
            return ToolResult.noMatch();
        }
        return ToolResult.found(rules);
    }
}
