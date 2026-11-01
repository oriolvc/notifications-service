package com.oriolvc.notificationsservice.notification.domain;

/** A template with the parameters filled in; {@code subject} is {@code null} for channels without one. */
public record RenderedMessage(String subject, String body) {}
