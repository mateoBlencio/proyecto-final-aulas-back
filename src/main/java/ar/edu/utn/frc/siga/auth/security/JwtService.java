package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import ar.edu.utn.frc.siga.auth.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties jwtProperties;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email, Set<Role> roles) {
        Instant now = Instant.now();
        Instant expiration = now.plus(Duration.ofMinutes(jwtProperties.getAccessExpirationMinutes()));

        return Jwts.builder()
                .subject(email)
                .claim(ROLES_CLAIM, roles.stream().map(Enum::name).collect(Collectors.toList()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey())
                .compact();
    }

    public long getAccessExpirationSeconds() {
        return Duration.ofMinutes(jwtProperties.getAccessExpirationMinutes()).toSeconds();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<Role> extractRoles(Claims claims) {
        List<String> roleNames = claims.get(ROLES_CLAIM, List.class);
        return roleNames.stream().map(Role::valueOf).collect(Collectors.toSet());
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
