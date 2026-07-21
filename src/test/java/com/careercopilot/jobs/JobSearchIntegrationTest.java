package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.source.NormalizedJob;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobSearchIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    repository.save(custom("1", "Senior Java Backend Engineer", "Acme", "Berlin", true, 140000));
    repository.save(custom("2", "Frontend React Developer", "Globex", "London", false, 90000));
    repository.save(custom("3", "Python Data Engineer", "Initech", "Berlin", true, 120000));
    repository.save(noSalary("4", "DevOps Engineer", "Umbrella Corp", "Remote", true));
  }

  @Test
  void search_byKeyword_matchesFullText() {
    JsonNode body = search("?q=java");
    assertThat(body.get("content")).hasSize(1);
    assertThat(body.get("content").get(0).get("title").asText()).contains("Java");
  }

  @Test
  void search_byLocation() {
    JsonNode body = search("?location=berlin");
    assertThat(body.get("content")).hasSize(2);
  }

  @Test
  void search_byRemoteType() {
    JsonNode body = search("?remoteType=onsite");
    assertThat(body.get("content")).hasSize(1);
    assertThat(body.get("content").get(0).get("company").asText()).isEqualTo("Globex");
  }

  @Test
  void search_bySalaryMin() {
    // Postings: (1) 140k-160k, (2) 90k-110k, (3) 120k-140k, (4) no salary listed. A $130k floor
    // should match any posting whose upper end clears the bar (compare against salary_max, not
    // salary_min) plus postings with no salary data at all, since we can't confirm those fall
    // short of the threshold.
    JsonNode body = search("?salaryMin=130000");
    assertThat(body.get("content")).hasSize(3);
  }

  @Test
  void search_invalidRemoteType_returns400() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/jobs/search?remoteType=nope", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private JsonNode search(String query) {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity("/api/v1/jobs/search" + query, JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private JobPosting custom(
      String externalId,
      String title,
      String company,
      String location,
      boolean remote,
      int salary) {
    NormalizedJob n =
        new NormalizedJob(
            JobSource.REMOTEOK,
            externalId,
            title,
            company,
            location,
            remote,
            "full_time",
            BigDecimal.valueOf(salary),
            BigDecimal.valueOf(salary + 20000),
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

  private JobPosting noSalary(
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
            null,
            null,
            null,
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
