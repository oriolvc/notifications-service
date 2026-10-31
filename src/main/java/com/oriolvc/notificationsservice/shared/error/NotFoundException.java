package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/** Missing resource, or a resource of another tenant (never reported as 403). */
public class NotFoundException extends ProblemException {

    public NotFoundException(String detail) {
        super(ProblemType.NOT_FOUND, detail, List.of());
    }
}
