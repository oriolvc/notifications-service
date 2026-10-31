package com.oriolvc.notificationsservice.shared.domain;

import java.time.Instant;

/** Who created and last changed an aggregate, and when. Filled by persistence on save. */
public record AuditMetadata(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {}
