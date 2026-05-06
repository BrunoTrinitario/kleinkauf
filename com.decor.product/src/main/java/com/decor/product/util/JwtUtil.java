/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.product.util;

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

    @Value("${api.internal.key}")
    private String apiSecret;


    public String generateMicroServiceToken(String serviceName){
        SecretKey apiKey = Keys.hmacShaKeyFor(apiSecret.getBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", serviceName);
        claims.put("subject", serviceName);
        return createToken(claims, 1000 * 10, apiKey);
    }

    private String createToken(Map<String, Object> claims, long expirationMillis, SecretKey key) {

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(claims.get("subject").toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key)
                .compact();
    }

}
