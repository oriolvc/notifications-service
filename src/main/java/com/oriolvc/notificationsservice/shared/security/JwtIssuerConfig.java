package com.oriolvc.notificationsservice.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;

/**
 * The issuer every token must carry. Configured separately from Boot's {@code issuer-uri} because setting that next to
 * {@code public-key-location} makes Boot switch to OIDC discovery and ignore the key. Boot adds this validator to the
 * decoder together with expiry, audience and {@link ServiceClaimsValidator}.
 */
@Configuration(proxyBeanMethods = false)
class JwtIssuerConfig {

    @Bean
    OAuth2TokenValidator<Jwt> jwtIssuerValidator(@Value("${notifications.security.jwt.issuer}") String issuer) {
        return new JwtIssuerValidator(issuer);
    }
}
