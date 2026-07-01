package com.careercopilot.shared;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

  private final Key signingKey;
  private final long expirySeconds;

  public JwtService(
      @Value("${jwt.secret}") String secret, @Value("${jwt.expiry-seconds}") long expirySeconds) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirySeconds = expirySeconds;
  }

  public String generateToken(UUID userId, String email) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(email)
        .claim("userId", userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirySeconds)))
        .signWith(signingKey)
        .compact();
  }

  public String extractSubject(String token) {
    return parseClaims(token).getSubject();
  }

  public boolean isValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public long getExpirySeconds() {
    return expirySeconds;
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith((javax.crypto.SecretKey) signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
