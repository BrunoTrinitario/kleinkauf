/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.kleinkauf.users.util;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.constants.TokenConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author bruno.trinitario
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    private static final String PASSWORD_CLAIM = "pwd";

    public static final long REFRESH_TOKEN_MILLIS = 1000L * 60 * 60 * 24 * 7; // 7 dias

    @PostConstruct
    public void init() {

        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // tenant: schema al que pertenece el usuario (los ids se repiten entre tenants);
    // el gateway rechaza el token si no coincide con el tenant de la request
    public String generateAccessToken(Long id, String email, String name, String tenant) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("name", name);
        claims.put("email", email);
        claims.put(TenantConstants.TOKEN_CLAIM, tenant);
        claims.put(TokenConstants.TYPE_CLAIM, TokenConstants.ACCESS);
        return createToken(claims, 1000 * 60 * 60); // 1h
    }

    // Renueva la sesion sin volver a pedir la contrasena (POST /auth/refresh). Como el de recuperacion, lleva la huella
    // de la contrasena: cambiarla corta todas las sesiones cuando vence su token de 1 hora.
    // jti: identifica el refresh para guardarlo en Redis y poder cortarlo solo a el (logout, rotacion)
    public String generateRefreshToken(Long id, String tenant, String passwordHash) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ID, UUID.randomUUID().toString());
        claims.put("id", id);
        claims.put(TenantConstants.TOKEN_CLAIM, tenant);
        claims.put(TokenConstants.TYPE_CLAIM, TokenConstants.REFRESH);
        claims.put(PASSWORD_CLAIM, passwordFingerprint(passwordHash));
        return createToken(claims, REFRESH_TOKEN_MILLIS);
    }

    // passwordHash: la contrasena actual (hasheada). El token deja de servir cuando la contrasena cambia,
    // asi que el link del mail se usa una sola vez
    public String generatePasswordToken(Long id, String email, String tenant, String passwordHash){
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("email", email);
        claims.put(TenantConstants.TOKEN_CLAIM, tenant);
        claims.put(TokenConstants.TYPE_CLAIM, TokenConstants.PASSWORD_RESET);
        claims.put(PASSWORD_CLAIM, passwordFingerprint(passwordHash));
        return createToken(claims, 1000 * 60 * 15); //15 min
    }

    // Huella de la contrasena hasheada: el token viaja en el mail y no tiene que llevar el hash
    public static String passwordFingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(passwordHash).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String createToken(Map<String, Object> claims, long expirationMillis) {

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key)
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

    public String getTenant(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get(TenantConstants.TOKEN_CLAIM, String.class) : null;
    }

    public String getType(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get(TokenConstants.TYPE_CLAIM, String.class) : null;
    }

    public String getTokenId(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getId() : null;
    }

    public String getPasswordFingerprint(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get(PASSWORD_CLAIM, String.class) : null;
    }

}
