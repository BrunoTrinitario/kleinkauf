/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author bruno.trinitario
*/
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${api.internal.key}")
    private String apiSecret;

    private SecretKey key, apiKey;

    @PostConstruct
    public void init() {

        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.apiKey = Keys.hmacShaKeyFor(apiSecret.getBytes());
    }

    public String generateAccessToken(Long id, String email, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("name", name);
        claims.put("email", email);
        return createToken(claims, 1000 * 60 * 60); // 1h
    }

    public String generateRefreshToken(Long id, String email, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("name", name);
        claims.put("email", email);
        return createToken(claims, 1000 * 60 * 60 * 24); // 24h
    }

    public String generateInternalToken(String microserviceName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("service-name", microserviceName);
        return createToken(claims, 1000 * 60 * 60); // 1h
    }

    private String createToken(Map<String, Object> claims, long expirationMillis) {

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key) // 🔥 ya no hace falta pasar algoritmo
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token, this.key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public boolean validateInternalToken(String token) {
        try {
            parseClaims(token, this.apiKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims getClaims(String token) {
        try {
            return parseClaims(token, this.key);
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get("id", Long.class) : null;
    }

    public String generateMicroServiceToken(String serviceName){
        SecretKey apiKey = Keys.hmacShaKeyFor(apiSecret.getBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", serviceName);
        claims.put("subject", serviceName);
        return createToken(claims, 1000 * 10, apiKey);
    }
    private String createToken(Map<String, Object> claims, long expirationMillis, SecretKey key) {

        return Jwts.builder()
                .claims(claims)
                .subject(claims.get("subject").toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key)
                .compact();
    }

}