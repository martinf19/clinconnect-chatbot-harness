package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.ContactMethod;
import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContactInfoToolServiceTest {

    @Autowired
    private ContactInfoToolService contactInfoToolService;

    private final AuthenticatedSubject subject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void returnsAllActiveContactsForTheProvider() {
        ToolResult<java.util.List<ContactMethod>> result =
                contactInfoToolService.getContactInfo(subject, "provider-avery-chen");

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(ContactMethod::getContactType)
                .containsExactlyInAnyOrder(ContactType.PAGER, ContactType.MOBILE);
    }

    @Test
    void returnsNoMatchForAProviderWithNoActiveContacts() {
        ToolResult<java.util.List<ContactMethod>> result =
                contactInfoToolService.getContactInfo(subject, "provider-does-not-exist");

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
    }
}
