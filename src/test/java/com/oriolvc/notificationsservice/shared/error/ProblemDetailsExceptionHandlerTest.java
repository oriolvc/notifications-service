package com.oriolvc.notificationsservice.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.shared.web.RequestIdFilter;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.ServletWebRequest;

class ProblemDetailsExceptionHandlerTest {

    private final ProblemDetailsExceptionHandler handler = new ProblemDetailsExceptionHandler();
    private final ServletWebRequest request =
            new ServletWebRequest(new MockHttpServletRequest("GET", "/distribution-lists/7"));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void notFound() {
        final ProblemDetail problem =
                body(handler.handleProblem(new NotFoundException("Distribution list 7"), request));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getType()).isEqualTo(URI.create("urn:notifications-service:problem:not-found"));
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Distribution list 7");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/distribution-lists/7"));
    }

    @Test
    void conflictsHaveDistinctTypes() {
        assertThat(body(handler.handleDataIntegrityViolation(integrityViolation(ConstraintKind.UNIQUE), request))
                        .getType())
                .isEqualTo(ProblemType.ALREADY_EXISTS.uri());
        assertThat(body(handler.handleProblem(new VersionConflictException("stale"), request))
                        .getType())
                .isEqualTo(ProblemType.VERSION_CONFLICT.uri());
        assertThat(body(handler.handleOptimisticLocking(
                                new ObjectOptimisticLockingFailureException(Object.class, 1L), request))
                        .getStatus())
                .isEqualTo(409);
    }

    @Test
    void integrityViolationsOtherThanUniqueAreUnexpected() {
        assertThat(body(handler.handleDataIntegrityViolation(integrityViolation(ConstraintKind.CHECK), request))
                        .getType())
                .isEqualTo(ProblemType.INTERNAL_ERROR.uri());
        assertThat(body(handler.handleDataIntegrityViolation(
                                new DataIntegrityViolationException("no Hibernate cause"), request))
                        .getStatus())
                .isEqualTo(500);
    }

    @Test
    void unprocessableCarriesViolationsAndRequestId() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-1");
        final var violations = List.of(new Violation("/parameters/confidence", "must be at most 100"));

        final ProblemDetail problem =
                body(handler.handleProblem(new UnprocessableException("Invalid parameters", violations), request));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(problem.getProperties())
                .containsEntry("violations", violations)
                .containsEntry("requestId", "req-1");
        assertThat(new UnprocessableException("Unknown type").violations()).isEmpty();
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() {
        final ProblemDetail problem =
                body(handler.handleUnexpected(new IllegalStateException("db password is x"), request));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getType()).isEqualTo(ProblemType.INTERNAL_ERROR.uri());
        assertThat(problem.getDetail()).doesNotContain("password");
    }

    @Test
    void fieldPathsBecomeJsonPointers() {
        assertThat(ProblemDetailsExceptionHandler.toPointer(new FieldError("request", "customerIds[0]", "bad")))
                .isEqualTo("/customerIds/0");
        assertThat(ProblemDetailsExceptionHandler.toPointer(new FieldError("request", "parameters.siteName", "bad")))
                .isEqualTo("/parameters/siteName");
    }

    @Test
    void statusesWithoutSpecificTypeFallBack() {
        assertThat(ProblemType.forStatus(409)).isEqualTo(ProblemType.VERSION_CONFLICT);
        assertThat(ProblemType.forStatus(418)).isEqualTo(ProblemType.MALFORMED_REQUEST);
        assertThat(ProblemType.forStatus(503)).isEqualTo(ProblemType.INTERNAL_ERROR);
    }

    private static ProblemDetail body(final ResponseEntity<Object> response) {
        return (ProblemDetail) response.getBody();
    }

    private static DataIntegrityViolationException integrityViolation(final ConstraintKind kind) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "violation", new SQLException("violation"), kind, "uq_some_constraint"));
    }
}
