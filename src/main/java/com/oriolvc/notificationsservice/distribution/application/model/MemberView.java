package com.oriolvc.notificationsservice.distribution.application.model;

import java.time.Instant;

public record MemberView(
        long id,
        String address,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
