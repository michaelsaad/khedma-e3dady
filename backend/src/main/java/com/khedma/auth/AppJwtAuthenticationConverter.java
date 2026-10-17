package com.khedma.auth;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.khedma.user.AppUser;
import com.khedma.user.UserRepository;

/**
 * Turns a verified access token into an authentication whose roles come from the database,
 * so role/status changes take effect immediately. Users that are not APPROVED get no roles.
 */
@Component
public class AppJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public AppJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt.getClaims().containsKey(JwtService.TYPE_CLAIM)) {
            // A sign-in challenge (password checked, emailed code not yet) is not an access token.
            throw new InvalidBearerTokenException("Not an access token");
        }
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        if (JwtService.versionOf(jwt) != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token was revoked");
        }

        List<GrantedAuthority> authorities = user.isApproved()
                ? user.getRole().impliedRoles().stream()
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList()
                : List.of();
        return new UsernamePasswordAuthenticationToken(AuthUser.of(user), jwt, authorities);
    }
}
