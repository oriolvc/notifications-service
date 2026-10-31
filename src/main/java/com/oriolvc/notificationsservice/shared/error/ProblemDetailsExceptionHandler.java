package com.oriolvc.notificationsservice.shared.error;

import com.oriolvc.notificationsservice.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
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
    ResponseEntity<Object> handleProblem(final ProblemException ex, final WebRequest request) {
        return problem(ex.type(), ex.getMessage(), ex.violations(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLocking(
            final ObjectOptimisticLockingFailureException ex, final WebRequest request) {
        return problem(
                ProblemType.VERSION_CONFLICT,
                "The resource was modified concurrently; read it again and retry",
                List.of(),
                request);
    }

    /**
     * Uniqueness rules (one active list per customer, type and channel; unique list names; …) are enforced by the
     * database's unique constraints, not checked beforehand: a unique violation is the 409. Any other integrity
     * violation means the application let invalid data through, so it stays a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrityViolation(
            final DataIntegrityViolationException ex, final WebRequest request) {
        if (ex.getCause() instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintKind.UNIQUE) {
            log.info("Unique constraint {} rejected the change", violation.getConstraintName());
            return problem(
                    ProblemType.ALREADY_EXISTS, "The change conflicts with an existing resource", List.of(), request);
        }
        return handleUnexpected(ex, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(final AuthenticationException ex, final WebRequest request) {
        return problem(ProblemType.UNAUTHORIZED, "A valid bearer token is required", List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(final AccessDeniedException ex, final WebRequest request) {
        return problem(ProblemType.FORBIDDEN, "The token lacks the role this operation requires", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(final Exception ex, final WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(ProblemType.INTERNAL_ERROR, "An unexpected error occurred", List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new Violation(toPointer(error), error.getDefaultMessage()))
                .toList();
        return problem(ProblemType.MALFORMED_REQUEST, "The request body is invalid", violations, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            final HandlerMethodValidationException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final List<Violation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Violation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return problem(ProblemType.MALFORMED_REQUEST, "The request parameters are invalid", violations, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            final HttpMessageNotReadableException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        return problem(ProblemType.MALFORMED_REQUEST, "The request body is not readable JSON", List.of(), request);
    }

    /** Every other Spring MVC exception: keep Spring's status and detail, add our type and request id. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            final Exception ex,
            final Object body,
            final HttpHeaders headers,
            final HttpStatusCode statusCode,
            final WebRequest request) {
        // Spring passes a null body for most of its exceptions and builds the ProblemDetail in super
        final ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            final ProblemType type = ProblemType.forStatus(statusCode.value());
            problemDetail.setType(type.uri());
            problemDetail.setTitle(type.title());
            decorate(problemDetail, request);
        }
        return response;
    }

    private ResponseEntity<Object> problem(
            final ProblemType type, final String detail, final List<Violation> violations, final WebRequest request) {
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problemDetail.setType(type.uri());
        problemDetail.setTitle(type.title());
        if (!violations.isEmpty()) {
            problemDetail.setProperty("violations", violations);
        }
        decorate(problemDetail, request);
        return ResponseEntity.status(type.status()).body(problemDetail);
    }

    private static void decorate(final ProblemDetail problemDetail, final WebRequest request) {
        if (problemDetail.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
            final HttpServletRequest httpRequest = servletRequest.getRequest();
            problemDetail.setInstance(URI.create(httpRequest.getRequestURI()));
        }
        final String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problemDetail.setProperty("requestId", requestId);
        }
    }

    /** {@code customerIds[0]} → {@code /customerIds/0}; {@code parameters.siteName} → {@code /parameters/siteName}. */
    static String toPointer(final FieldError error) {
        return "/" + error.getField().replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
    }
}
