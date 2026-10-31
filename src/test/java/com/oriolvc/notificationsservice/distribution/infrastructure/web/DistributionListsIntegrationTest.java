package com.oriolvc.notificationsservice.distribution.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class DistributionListsIntegrationTest extends IntegrationTest {

    private static final String ADMIN_101 = JwtTokens.listAdmin(101);
    private static final String ADMIN_101_USER_ID = "user-101-distribution_list_admin";
    private static final String CREATE_BODY = """
            {"name": "  Supplier compliance  ", "notificationType": "REGULATORY_DOCUMENT_MATCHED", "channel": "EMAIL"}
            """;

    @Nested
    class Create {

        @Test
        void createsAnActiveListInTheCallersTenant() {
            final MvcTestResult result = post("/distribution-lists", ADMIN_101, CREATE_BODY);

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).headers().hasValue(HttpHeaders.LOCATION, "http://localhost/distribution-lists/1");
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"id": 1, "name": "Supplier compliance", "notificationType": "REGULATORY_DOCUMENT_MATCHED",
                     "channel": "EMAIL", "active": true, "version": 0,
                     "createdBy": "user-101-distribution_list_admin", "updatedBy": "user-101-distribution_list_admin"}
                    """);
            assertThat(jdbc.queryForObject("SELECT customer_id FROM distribution_lists WHERE id = 1", Long.class))
                    .isEqualTo(101);
        }

        @Test
        @Sql("/fixtures/distribution-lists.sql")
        @DisplayName("AC-21 second active list for the same (customer, type, channel) is a conflict")
        void secondActiveListIsAConflict() {
            final MvcTestResult result = post("/distribution-lists", ADMIN_101, CREATE_BODY);

            assertConflict(result, "already-exists");
            assertThat(countLists()).isEqualTo(3);
        }

        @Test
        @Sql("/fixtures/distribution-lists.sql")
        void duplicateNameIsAConflict() {
            jdbc.update("UPDATE distribution_lists SET active = false WHERE id = 1001");

            assertConflict(post("/distribution-lists", ADMIN_101, """
                            {"name": "Compliance team", "notificationType": "REGULATORY_DOCUMENT_MATCHED",
                             "channel": "EMAIL"}
                            """), "already-exists");
        }

        @Test
        void unknownNotificationTypeIsUnprocessable() {
            final MvcTestResult result = post("/distribution-lists", ADMIN_101, """
                    {"name": "Lists", "notificationType": "UNKNOWN_TYPE", "channel": "EMAIL"}
                    """);

            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.violations[0].pointer")
                    .isEqualTo("/notificationType");
            assertThat(countLists()).isZero();
        }

        @Test
        void blankNameIsMalformed() {
            final MvcTestResult result = post("/distribution-lists", ADMIN_101, """
                    {"name": "   ", "notificationType": "REGULATORY_DOCUMENT_MATCHED", "channel": "EMAIL"}
                    """);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.violations[0].pointer")
                    .isEqualTo("/name");
        }
    }

    @Nested
    @Sql("/fixtures/distribution-lists.sql")
    class Read {

        @Test
        void listsOnlyTheCallersTenantIncludingInactiveLists() {
            final MvcTestResult result = get("/distribution-lists", JwtTokens.viewer(101));

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.items[*].id").isEqualTo(List.of(1001, 1002));
            assertThat(result).bodyJson().extractingPath("$.items[*].active").isEqualTo(List.of(true, false));
        }

        @Test
        void getsOwnList() {
            final MvcTestResult result = get("/distribution-lists/1001", JwtTokens.viewer(101));

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Compliance team");
        }
    }

    @Nested
    @Sql("/fixtures/distribution-lists.sql")
    @DisplayName("AC-18 a user of tenant 101 cannot see or change a list of tenant 102")
    class CrossTenant {

        @Test
        void readIsNotFound() {
            assertNotFound(get("/distribution-lists/2001", ADMIN_101));
        }

        @Test
        void updateIsNotFoundAndChangesNothing() {
            assertNotFound(patch("/distribution-lists/2001", ADMIN_101, "{\"name\": \"Hijacked\", \"version\": 0}"));
            assertThat(listRow(2001)).containsEntry("name", "Quality alerts").containsEntry("version", 0L);
        }

        @Test
        void deleteIsNotFoundAndChangesNothing() {
            assertNotFound(delete("/distribution-lists/2001", ADMIN_101));
            assertThat(listRow(2001)).containsEntry("active", true);
        }
    }

    @Nested
    @Sql("/fixtures/distribution-lists.sql")
    class Update {

        @Test
        void renamesAndBumpsTheVersion() {
            final MvcTestResult result =
                    patch("/distribution-lists/1001", ADMIN_101, "{\"name\": \"QA team\", \"version\": 0}");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"id": 1001, "name": "QA team", "active": true, "version": 1,
                     "createdBy": "fixture", "updatedBy": "user-101-distribution_list_admin"}
                    """);
        }

        @Test
        @DisplayName("AC-20 update with a stale version is a conflict")
        void staleVersionIsAConflict() {
            assertThat(patch("/distribution-lists/1001", ADMIN_101, "{\"name\": \"First\", \"version\": 0}"))
                    .hasStatusOk();

            final MvcTestResult stale =
                    patch("/distribution-lists/1001", ADMIN_101, "{\"name\": \"Second\", \"version\": 0}");

            assertConflict(stale, "version-conflict");
            assertThat(listRow(1001)).containsEntry("name", "First").containsEntry("version", 1L);
        }

        @Test
        void renamingToAnotherListsNameIsAConflict() {
            assertConflict(
                    patch("/distribution-lists/1001", ADMIN_101, "{\"name\": \"Old compliance team\", \"version\": 0}"),
                    "already-exists");
        }

        @Test
        void reactivatingWhileAnotherListIsActiveIsAConflict() {
            assertConflict(
                    patch("/distribution-lists/1002", ADMIN_101, "{\"active\": true, \"version\": 0}"),
                    "already-exists");
        }

        @Test
        void deactivatingOneListAllowsReactivatingAnother() {
            assertThat(patch("/distribution-lists/1001", ADMIN_101, "{\"active\": false, \"version\": 0}"))
                    .hasStatusOk();

            final MvcTestResult result =
                    patch("/distribution-lists/1002", ADMIN_101, "{\"active\": true, \"version\": 0}");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
        }
    }

    @Nested
    @Sql("/fixtures/distribution-lists.sql")
    class Delete {

        @Test
        void softDeletesAndIsIdempotent() {
            assertThat(delete("/distribution-lists/1001", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(delete("/distribution-lists/1001", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);

            assertThat(listRow(1001)).containsEntry("active", false).containsEntry("version", 1L);
        }
    }

    @Test
    @DisplayName("AC-3 (lists) every change creates an Envers revision with the user id")
    void everyChangeIsAuditedWithTheUserId() {
        assertThat(post("/distribution-lists", ADMIN_101, CREATE_BODY)).hasStatus(HttpStatus.CREATED);
        assertThat(patch("/distribution-lists/1", ADMIN_101, "{\"name\": \"Renamed\", \"version\": 0}"))
                .hasStatusOk();
        assertThat(delete("/distribution-lists/1", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);

        final List<Map<String, Object>> revisions = jdbc.queryForList("""
                SELECT a.revtype, a.name, a.active, r.user_id
                FROM distribution_lists_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = 1 ORDER BY a.rev
                """);

        assertThat(revisions)
                .extracting("revtype", "name", "active", "user_id")
                .containsExactly(
                        tuple(0, "Supplier compliance", true, ADMIN_101_USER_ID),
                        tuple(1, "Renamed", true, ADMIN_101_USER_ID),
                        tuple(1, "Renamed", false, ADMIN_101_USER_ID));
    }

    private void assertConflict(final MvcTestResult result, final String problemType) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:notifications-service:problem:" + problemType);
    }

    private void assertNotFound(final MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:notifications-service:problem:not-found");
    }

    private Integer countLists() {
        return jdbc.queryForObject("SELECT count(*) FROM distribution_lists", Integer.class);
    }

    private Map<String, Object> listRow(final long id) {
        return jdbc.queryForMap("SELECT name, active, version FROM distribution_lists WHERE id = ?", id);
    }

    private MvcTestResult get(final String uri, final String token) {
        return mvc.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult post(final String uri, final String token, final String body) {
        return mvc.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult patch(final String uri, final String token, final String body) {
        return mvc.patch()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult delete(final String uri, final String token) {
        return mvc.delete()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }
}
