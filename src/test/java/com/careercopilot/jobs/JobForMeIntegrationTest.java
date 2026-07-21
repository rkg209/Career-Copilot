package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.jobs.source.NormalizedJob;
import com.careercopilot.profile.CandidateProfile;
import com.careercopilot.profile.CandidateProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobForMeIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JobPostingRepository jobRepository;
  @Autowired private CandidateProfileRepository profileRepository;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    jobRepository.deleteAll();
    jobRepository.save(custom("1", "Senior Backend Engineer", "Acme", "Berlin", true));
    jobRepository.save(custom("2", "Frontend React Developer", "Globex", "London", false));
  }

  @Test
  void forMe_matchesJobsAgainstTargetRolesAndInterests() {
    String email = "prefs@example.com";
    String token = registerAndLogin(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    CandidateProfile profile = new CandidateProfile(user.getId());
    profile.setTargetRoles(List.of("Backend Engineer"));
    profileRepository.save(profile);

    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/jobs/for-me", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("content")).hasSize(1);
    assertThat(response.getBody().get("content").get(0).get("company").asText()).isEqualTo("Acme");
  }

  @Test
  void forMe_noPreferencesSet_returnsEmptyPage() {
    String token = registerAndLogin("noprefs@example.com");

    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            "/api/v1/jobs/for-me", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("content")).isEmpty();
  }

  @Test
  void forMe_anonymous_returns401() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/jobs/for-me", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private String registerAndLogin(String email) {
    restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequestDto(email, "password123"), Object.class);
    ResponseEntity<AuthResponseDto> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login", new LoginRequestDto(email, "password123"), AuthResponseDto.class);
    return login.getBody().token();
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private JobPosting custom(
      String externalId, String title, String company, String location, boolean remote) {
    NormalizedJob n =
        new NormalizedJob(
            JobSource.REMOTEOK,
            externalId,
            title,
            company,
            location,
            remote,
            "full_time",
            BigDecimal.valueOf(100000),
            BigDecimal.valueOf(120000),
            "USD",
            List.of(),
            List.of(),
            title + " " + company,
            "https://example.test/" + externalId,
            Instant.parse("2026-06-20T00:00:00Z"),
            null,
            null);
    return JobPostingMapper.toEntity(n);
  }
}
