package com.oriolvc.notificationsservice.shared.auditing;

import com.oriolvc.notificationsservice.shared.security.CurrentPrincipal;
import com.oriolvc.notificationsservice.shared.security.CurrentPrincipalProvider;

/** Who is changing data: the caller's id, or {@value #SYSTEM} outside a request (e.g. the dispatcher). */
final class Auditor {

    static final String SYSTEM = "system";

    private Auditor() {}

    static String current() {
        return CurrentPrincipalProvider.find().map(CurrentPrincipal::userId).orElse(SYSTEM);
    }
}
