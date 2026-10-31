package com.oriolvc.notificationsservice.shared.error;

import com.oriolvc.notificationsservice.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Builds every error response as an RFC 9457 Problem Detail with a stable {@link ProblemType}, the request id and,
 * for validation errors, the list of {@link Violation}s. Also renders the security 401/403 (see
 * {@code ProblemDetailsAuthenticationEntryPoint}).
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

    @ExceptionHandler(ProblemException.class)
    ResponseEntity<Object> handleProblem(ProblemException ex, WebRequest request) {
        return problem(ex.type(), ex.getMessage(), ex.violations(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex, WebRequest request) {
        return problem(
                ProblemType.VERSION_CONFLICT,
                "The resource was modified concurrently; read it again and retry",
                List.of(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return problem(ProblemType.UNAUTHORIZED, "A valid bearer token is required", List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return problem(ProblemType.FORBIDDEN, "The token lacks the role this operation requires", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(ProblemType.INTERNAL_ERROR, "An unexpected error occurred", List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new Violation(toPointer(error), error.getDefaultMessage()))
                .toList();
        return problem(ProblemType.MALFORMED_REQUEST, "The request body is invalid", violations, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Violation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Violation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return problem(ProblemType.MALFORMED_REQUEST, "The request parameters are invalid", violations, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ProblemType.MALFORMED_REQUEST, "The request body is not readable JSON", List.of(), request);
    }

    /** Every other Spring MVC exception: keep Spring's status and detail, add our type and request id. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Spring passes a null body for most of its exceptions and builds the ProblemDetail in super
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            ProblemType type = ProblemType.forStatus(statusCode.value());
            problemDetail.setType(type.uri());
            problemDetail.setTitle(type.title());
            decorate(problemDetail, request);
        }
        return response;
    }

    private ResponseEntity<Object> problem(
            ProblemType type, String detail, List<Violation> violations, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problemDetail.setType(type.uri());
        problemDetail.setTitle(type.title());
        if (!violations.isEmpty()) {
            problemDetail.setProperty("violations", violations);
        }
        decorate(problemDetail, request);
        return ResponseEntity.status(type.status()).body(problemDetail);
    }

    private static void decorate(ProblemDetail problemDetail, WebRequest request) {
        if (problemDetail.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getRequest();
            problemDetail.setInstance(URI.create(httpRequest.getRequestURI()));
        }
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problemDetail.setProperty("requestId", requestId);
        }
    }

    /** {@code customerIds[0]} → {@code /customerIds/0}; {@code parameters.siteName} → {@code /parameters/siteName}. */
    static String toPointer(FieldError error) {
        return "/" + error.getField().replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
    }
}
