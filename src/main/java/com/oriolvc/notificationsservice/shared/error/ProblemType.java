package com.oriolvc.notificationsservice.shared.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

/** Stable problem types: clients may rely on {@link #uri()}, never on titles or details. */
public enum ProblemType {
    MALFORMED_REQUEST("malformed-request", HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    NOT_ACCEPTABLE("not-acceptable", HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),
    VERSION_CONFLICT("version-conflict", HttpStatus.CONFLICT, "Version conflict"),
    ALREADY_EXISTS("already-exists", HttpStatus.CONFLICT, "Resource already exists"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    UNPROCESSABLE_CONTENT("unprocessable-content", HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable content"),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private static final String URI_PREFIX = "urn:notifications-service:problem:";

    private final URI uri;
    private final HttpStatus status;
    private final String title;

    ProblemType(final String slug, final HttpStatus status, final String title) {
        this.uri = URI.create(URI_PREFIX + slug);
        this.status = status;
        this.title = title;
    }

    public URI uri() {
        return uri;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** The type used for a status that has no more specific problem type. */
    static ProblemType forStatus(final int status) {
        for (final ProblemType type : values()) {
            if (type.status.value() == status && type != ALREADY_EXISTS) {
                return type;
            }
        }
        return status >= 500 ? INTERNAL_ERROR : MALFORMED_REQUEST;
    }
}
