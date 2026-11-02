package com.oriolvc.notificationsservice.notification.application.ports;

import com.oriolvc.notificationsservice.notification.domain.Recipient;
import java.util.List;

/** Port to the distribution context: who receives a notification of a type for a customer. */
public interface RecipientResolver {

    /** The active members of the customer's active lists for the type, one list per channel; empty if none. */
    List<Recipient> resolve(long customerId, long notificationTypeId);
}
