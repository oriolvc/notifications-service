package com.oriolvc.notificationsservice.distribution.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.distribution.application.model.Recipient;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@Sql("/fixtures/distribution-lists.sql")
class ActiveRecipientsQueryIntegrationTest extends IntegrationTest {

    @Autowired
    private ActiveRecipientsQuery query;

    private long typeId() {
        return jdbc.queryForObject(
                "SELECT id FROM notification_types WHERE code = 'REGULATORY_DOCUMENT_MATCHED'", Long.class);
    }

    @Test
    void activeMembersOfTheCustomersActiveListsOnly() {
        assertThat(query.findActiveRecipients(101, typeId()))
                .containsExactly(
                        new Recipient(1001, 1101, Channel.EMAIL, "alice@customer101.example"),
                        new Recipient(1001, 1102, Channel.EMAIL, "bob@customer101.example"));
        assertThat(query.findActiveRecipients(102, typeId()))
                .containsExactly(new Recipient(2001, 2101, Channel.EMAIL, "dave@customer102.example"));
    }

    @Test
    void aCustomerWithoutAnActiveListHasNoRecipients() {
        jdbc.update("UPDATE distribution_lists SET active = false WHERE id = 2001");

        assertThat(query.findActiveRecipients(102, typeId())).isEmpty();
        assertThat(query.findActiveRecipients(103, typeId())).isEmpty();
    }
}
