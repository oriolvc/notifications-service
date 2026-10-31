package com.oriolvc.notificationsservice.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** 403: sets {@code WWW-Authenticate} (RFC 6750), then lets the MVC exception handler write the Problem Detail. */
@Component
class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    private final HandlerExceptionResolver resolver;

    ProblemDetailsAccessDeniedHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        bearer.handle(request, response, exception);
        resolver.resolveException(request, response, null, exception);
    }
}
