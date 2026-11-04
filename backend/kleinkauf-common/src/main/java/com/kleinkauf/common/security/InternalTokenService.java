package com.kleinkauf.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/*
Generates and validates the JWTs the microservices use to talk to each other (/api/internal/**).
They are signed with api.internal.key, different from the user token key.
 */
public class InternalTokenService {

    private static final long EXPIRATION_MILLIS = 1000 * 10; // 10 seg

    private final SecretKey apiKey;

    public InternalTokenService(String apiSecret) {
        this.apiKey = Keys.hmacShaKeyFor(apiSecret.getBytes());
    }

    public String generateMicroServiceToken(String serviceName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", serviceName);
        return Jwts.builder()
                .claims(claims)
                .subject(serviceName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MILLIS))
                .signWith(apiKey)
                .compact();
    }

    public boolean validateInternalToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(apiKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
