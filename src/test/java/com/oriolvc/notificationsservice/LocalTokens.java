package com.oriolvc.notificationsservice;

import com.oriolvc.notificationsservice.shared.security.Role;
import java.time.Duration;
import java.util.List;

/**
 * Prints a 12-hour token per role for trying the API locally ({@code local} profile):
 *
 * <pre>./mvnw -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.oriolvc.notificationsservice.LocalTokens</pre>
 */
public final class LocalTokens {

    private static final Duration VALIDITY = Duration.ofHours(12);

    private LocalTokens() {}

    public static void main(String[] args) {
        print(
                "NOTIFICATION_PUBLISHER (detection process)",
                JwtTokens.token(
                        claims -> claims.subject(JwtTokens.PUBLISHER_CLIENT_ID)
                                .claim("client_id", JwtTokens.PUBLISHER_CLIENT_ID)
                                .claim("roles", List.of(Role.NOTIFICATION_PUBLISHER.name())),
                        VALIDITY));
        for (long tenantId : new long[] {101, 102}) {
            for (Role role : List.of(Role.DISTRIBUTION_LIST_ADMIN, Role.NOTIFICATION_VIEWER)) {
                String userId = "user-" + tenantId + "-" + role.name().toLowerCase();
                print(
                        role + " of tenant " + tenantId,
                        JwtTokens.token(
                                claims -> claims.subject(userId)
                                        .claim("userId", userId)
                                        .claim("tenantId", tenantId)
                                        .claim("roles", List.of(role.name())),
                                VALIDITY));
            }
        }
    }

    private static void print(String label, String token) {
        System.out.println("# " + label);
        System.out.println(token);
        System.out.println();
    }
}
