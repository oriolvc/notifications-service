package com.oriolvc.notificationsservice;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.oriolvc.notificationsservice.shared.security.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs JWTs with the dev key pair the {@code local} and {@code test} profiles trust. */
public final class JwtTokens {

    public static final String ISSUER = "http://localhost/local-issuer";
    public static final String AUDIENCE = "notifications-service";
    public static final String PUBLISHER_CLIENT_ID = "detection-process";

    private static final RSAKey DEV_KEY = loadDevKey();
    private static final JwtEncoder DEV_ENCODER = encoder(DEV_KEY);

    private JwtTokens() {}

    public static String publisher() {
        return token(claims -> claims.claim("client_id", PUBLISHER_CLIENT_ID)
                .subject(PUBLISHER_CLIENT_ID)
                .claim("roles", List.of(Role.NOTIFICATION_PUBLISHER.name())));
    }

    public static String listAdmin(final long tenantId) {
        return user(tenantId, Role.DISTRIBUTION_LIST_ADMIN);
    }

    public static String viewer(final long tenantId) {
        return user(tenantId, Role.NOTIFICATION_VIEWER);
    }

    /** A customer user of {@code tenantId} with user id {@code user-<tenantId>-<first role>}. */
    public static String user(final long tenantId, final Role... roles) {
        final String userId = "user-" + tenantId + "-" + roles[0].name().toLowerCase();
        return token(claims -> claims.subject(userId)
                .claim("userId", userId)
                .claim("tenantId", tenantId)
                .claim("roles", Arrays.stream(roles).map(Role::name).toList()));
    }

    /** A valid token (issuer, audience, one hour expiry) with the given claims on top. */
    public static String token(final Consumer<JwtClaimsSet.Builder> claims) {
        return sign(DEV_ENCODER, claims, Duration.ofHours(1));
    }

    static String token(final Consumer<JwtClaimsSet.Builder> claims, final Duration validity) {
        return sign(DEV_ENCODER, claims, validity);
    }

    /** A token that is valid except for being signed with an unknown key. */
    public static String signedWithForeignKey(final Consumer<JwtClaimsSet.Builder> claims) {
        try {
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            final var keyPair = generator.generateKeyPair();
            final RSAKey foreign = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .build();
            return sign(encoder(foreign), claims, Duration.ofHours(1));
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String bearer(final String token) {
        return "Bearer " + token;
    }

    private static String sign(
            final JwtEncoder encoder, final Consumer<JwtClaimsSet.Builder> customizer, final Duration validity) {
        final Instant now = Instant.now();
        final JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(validity));
        customizer.accept(claims);
        final JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static JwtEncoder encoder(final RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static RSAKey loadDevKey() {
        try {
            final String pem =
                    new ClassPathResource("keys/local-jwt-private.pem").getContentAsString(StandardCharsets.UTF_8);
            final byte[] der = Base64.getMimeDecoder()
                    .decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            final var privateKey = (RSAPrivateCrtKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
            final var publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
