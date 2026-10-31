package com.oriolvc.notificationsservice.shared.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Maps the {@code roles} claim to {@code ROLE_*} authorities and names the authentication after the caller id. */
class PrincipalJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = JwtClaims.roles(jwt).stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, JwtClaims.identity(jwt));
    }
}
