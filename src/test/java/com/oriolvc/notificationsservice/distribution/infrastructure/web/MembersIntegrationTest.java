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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Sql("/fixtures/distribution-lists.sql")
class MembersIntegrationTest extends IntegrationTest {

    private static final String ADMIN_101 = JwtTokens.listAdmin(101);
    private static final String ADMIN_101_USER_ID = "user-101-distribution_list_admin";
    private static final String VIEWER_101 = JwtTokens.viewer(101);
    private static final String MEMBERS_1001 = "/distribution-lists/1001/members";

    @Nested
    class Add {

        @Test
        void addsATrimmedActiveMember() {
            final MvcTestResult result = post(MEMBERS_1001, ADMIN_101, "{\"address\": \" eve@customer101.example \"}");

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result)
                    .headers()
                    .hasValue(HttpHeaders.LOCATION, "http://localhost/distribution-lists/1001/members/1");
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"id": 1, "address": "eve@customer101.example", "active": true,
                     "createdBy": "user-101-distribution_list_admin", "updatedBy": "user-101-distribution_list_admin"}
                    """);
        }

        @Test
        @DisplayName("AC-19 an invalid email address is a bad request")
        void invalidAddressIsABadRequest() {
            final MvcTestResult result = post(MEMBERS_1001, ADMIN_101, "{\"address\": \"eve@customer101\"}");

            assertProblem(result, HttpStatus.BAD_REQUEST, "malformed-request");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.violations[0].pointer")
                    .isEqualTo("/address");
            assertThat(result).bodyJson().extractingPath("$.detail").asString().doesNotContain("eve@");
            assertThat(countMembers(1001)).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-19 an address that is already active is a conflict, ignoring case")
        void alreadyActiveAddressIsAConflict() {
            assertProblem(
                    post(MEMBERS_1001, ADMIN_101, "{\"address\": \"ALICE@customer101.example\"}"),
                    HttpStatus.CONFLICT,
                    "already-exists");
            assertThat(countMembers(1001)).isEqualTo(3);
        }

        @Test
        void reAddingARemovedAddressReactivatesIt() {
            final MvcTestResult result = post(MEMBERS_1001, ADMIN_101, "{\"address\": \"carol@customer101.example\"}");

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result)
                    .headers()
                    .hasValue(HttpHeaders.LOCATION, "http://localhost/distribution-lists/1001/members/1103");
            assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
            assertThat(countMembers(1001)).isEqualTo(3);
        }

        @Test
        void aListHoldsAtMostOneHundredActiveMembers() {
            jdbc.update("""
                    INSERT INTO distribution_list_members (distribution_list_id, address, created_at, created_by,
                                                           updated_at, updated_by)
                    SELECT 1001, 'member' || n || '@customer101.example', now(), 'fixture', now(), 'fixture'
                    FROM generate_series(1, 98) AS n
                    """);

            assertProblem(
                    post(MEMBERS_1001, ADMIN_101, "{\"address\": \"eve@customer101.example\"}"),
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "unprocessable-content");
        }

        @Test
        void changingMembersBumpsTheListVersion() {
            assertThat(post(MEMBERS_1001, ADMIN_101, "{\"address\": \"eve@customer101.example\"}"))
                    .hasStatus(HttpStatus.CREATED);

            assertThat(get("/distribution-lists/1001", ADMIN_101))
                    .bodyJson()
                    .extractingPath("$.version")
                    .isEqualTo(1);
        }

        @Test
        void anotherTenantsListIsNotFound() {
            assertProblem(
                    post("/distribution-lists/2001/members", ADMIN_101, "{\"address\": \"eve@customer101.example\"}"),
                    HttpStatus.NOT_FOUND,
                    "not-found");
        }
    }

    @Nested
    class Read {

        @Test
        void listsAllMembersIncludingRemovedOnes() {
            final MvcTestResult result = get(MEMBERS_1001, VIEWER_101);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"items": [
                      {"id": 1101, "address": "alice@customer101.example", "active": true},
                      {"id": 1102, "address": "bob@customer101.example", "active": true},
                      {"id": 1103, "address": "carol@customer101.example", "active": false}]}
                    """);
        }

        @Test
        void anotherTenantsListIsNotFound() {
            assertProblem(get("/distribution-lists/2001/members", VIEWER_101), HttpStatus.NOT_FOUND, "not-found");
        }
    }

    @Nested
    class Remove {

        @Test
        void softDeletesAndIsIdempotent() {
            assertThat(delete(MEMBERS_1001 + "/1101", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(delete(MEMBERS_1001 + "/1101", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);

            assertThat(jdbc.queryForObject(
                            "SELECT active FROM distribution_list_members WHERE id = 1101", Boolean.class))
                    .isFalse();
        }

        @Test
        void aMemberOfAnotherListIsNotFound() {
            assertProblem(delete(MEMBERS_1001 + "/1201", ADMIN_101), HttpStatus.NOT_FOUND, "not-found");
            assertProblem(delete(MEMBERS_1001 + "/9999", ADMIN_101), HttpStatus.NOT_FOUND, "not-found");
        }

        @Test
        void anotherTenantsListIsNotFound() {
            assertProblem(
                    delete("/distribution-lists/2001/members/2101", ADMIN_101), HttpStatus.NOT_FOUND, "not-found");
            assertThat(jdbc.queryForObject(
                            "SELECT active FROM distribution_list_members WHERE id = 2101", Boolean.class))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("AC-3 create a list, add and remove a member, deactivate the list: every change is audited")
    void listManagementIsAuditedWithTheUserId() {
        jdbc.update("UPDATE distribution_lists SET active = false WHERE id = 1001");
        assertThat(post("/distribution-lists", ADMIN_101, """
                        {"name": "Audit trail", "notificationType": "REGULATORY_DOCUMENT_MATCHED", "channel": "EMAIL"}
                        """)).hasStatus(HttpStatus.CREATED);
        assertThat(post("/distribution-lists/1/members", ADMIN_101, "{\"address\": \"eve@customer101.example\"}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(delete("/distribution-lists/1/members/1", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete("/distribution-lists/1", ADMIN_101)).hasStatus(HttpStatus.NO_CONTENT);

        final List<Map<String, Object>> memberRevisions = jdbc.queryForList("""
                SELECT a.revtype, a.address, a.active, r.user_id
                FROM distribution_list_members_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = 1 ORDER BY a.rev
                """);
        assertThat(memberRevisions)
                .extracting("revtype", "address", "active", "user_id")
                .containsExactly(
                        tuple(0, "eve@customer101.example", true, ADMIN_101_USER_ID),
                        tuple(1, "eve@customer101.example", false, ADMIN_101_USER_ID));

        final List<Map<String, Object>> listRevisions = jdbc.queryForList("""
                SELECT a.revtype, a.active, r.user_id
                FROM distribution_lists_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = 1 ORDER BY a.rev
                """);
        assertThat(listRevisions).extracting("user_id").containsOnly(ADMIN_101_USER_ID);
        assertThat(listRevisions)
                .extracting("revtype", "active")
                .startsWith(tuple(0, true))
                .endsWith(tuple(1, false));
    }

    private Integer countMembers(final long listId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM distribution_list_members WHERE distribution_list_id = ?", Integer.class, listId);
    }
}
