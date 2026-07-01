package com.careercopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "unit-test-signing-secret-that-is-at-least-32-bytes";

  @Test
  void generateAndExtractSubject_roundTrips() {
    JwtService jwtService = new JwtService(SECRET, 3600);
    UUID userId = UUID.randomUUID();
    String token = jwtService.generateToken(userId, "user@example.com");

    assertThat(jwtService.isValid(token)).isTrue();
    assertThat(jwtService.extractSubject(token)).isEqualTo("user@example.com");
  }

  @Test
  void isValid_falseForTokenSignedWithDifferentKey() {
    JwtService signer = new JwtService(SECRET, 3600);
    JwtService verifier =
        new JwtService("a-completely-different-signing-secret-32-bytes-min", 3600);
    String token = signer.generateToken(UUID.randomUUID(), "user@example.com");

    assertThat(verifier.isValid(token)).isFalse();
  }

  @Test
  void isValid_falseForExpiredToken() {
    JwtService jwtService = new JwtService(SECRET, -1);
    String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com");

    assertThat(jwtService.isValid(token)).isFalse();
  }

  @Test
  void isValid_falseForGarbageToken() {
    JwtService jwtService = new JwtService(SECRET, 3600);

    assertThat(jwtService.isValid("not-a-valid-jwt")).isFalse();
  }

  @Test
  void getExpirySeconds_returnsConfiguredValue() {
    JwtService jwtService = new JwtService(SECRET, 86400);

    assertThat(jwtService.getExpirySeconds()).isEqualTo(86400);
  }
}
