package com.oriolvc.notificationsservice;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Checks every MockMvc exchange against the OpenAPI spec (RFC 15):
 *
 * <ul>
 *   <li>responses must always match the spec (status, content type, body schema);
 *   <li>requests must match it unless the server rejected them with a 4xx, because negative tests send invalid
 *       requests on purpose;
 *   <li>501 from not-yet-implemented generated defaults and paths outside the spec (docs, actuator) are skipped.
 * </ul>
 */
final class OpenApiContractMatcher implements ResultMatcher {

    static final String SPEC = "openapi/notifications-service.yaml";

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator.createForSpecificationUrl(
                    specUrl())
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request.path.missing", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    @Override
    public void match(MvcResult result) {
        int status = result.getResponse().getStatus();
        if (status == HttpStatus.NOT_IMPLEMENTED.value()) {
            return;
        }
        boolean rejectedByServer = status >= 400 && status < 500;
        ValidationReport report =
                VALIDATOR.validate(MockMvcRequest.of(result.getRequest()), MockMvcResponse.of(result.getResponse()));
        List<ValidationReport.Message> errors = report.getMessages().stream()
                .filter(message -> message.getLevel() == ValidationReport.Level.ERROR)
                .filter(message -> !(rejectedByServer && message.getKey().startsWith("validation.request")))
                .toList();
        if (!errors.isEmpty()) {
            throw new AssertionError("Exchange violates the OpenAPI spec:\n"
                    + errors.stream()
                            .map(message -> "- [" + message.getKey() + "] " + message.getMessage())
                            .collect(Collectors.joining("\n")));
        }
    }

    private static String specUrl() {
        try {
            return new ClassPathResource(SPEC).getURL().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
