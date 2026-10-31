package com.oriolvc.notificationsservice.shared.error;

/**
 * One validation failure.
 *
 * @param pointer JSON Pointer (RFC 6901) into the request body, e.g. {@code /parameters/confidence}, or the name of
 *     the offending path or query parameter
 * @param message what is wrong. Never echoes the rejected value
 */
public record Violation(String pointer, String message) {}
