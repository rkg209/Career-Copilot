package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
class JobBrowseIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JobPostingRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void list_returnsActivePostings_publicly() {
    repository.save(JobTestFixtures.entity(JobSource.REMOTEOK, "1", "Backend Engineer", "Acme"));
    repository.save(JobTestFixtures.entity(JobSource.REMOTEOK, "2", "Data Engineer", "Globex"));

    ResponseEntity<JsonNode> response = restTemplate.getForEntity("/api/v1/jobs", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("content")).hasSize(2);
    assertThat(response.getBody().get("page").get("totalElements").asInt()).isEqualTo(2);
  }

  @Test
  void get_returnsDetail_and404ForMissing() {
    JobPosting saved =
        repository.save(JobTestFixtures.entity(JobSource.REMOTEOK, "10", "SRE", "Initech"));

    ResponseEntity<JsonNode> found =
        restTemplate.getForEntity("/api/v1/jobs/" + saved.getId(), JsonNode.class);
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(found.getBody().get("company").asText()).isEqualTo("Initech");
    assertThat(found.getBody().get("responsibilities")).hasSize(1);

    ResponseEntity<String> missing = restTemplate.getForEntity("/api/v1/jobs/999999", String.class);
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void get_inactivePosting_returns404() {
    JobPosting posting = JobTestFixtures.entity(JobSource.REMOTEOK, "11", "QA", "Umbrella");
    posting.setActive(false);
    JobPosting saved = repository.save(posting);

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/jobs/" + saved.getId(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void list_sizeAbove100_returns400() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/jobs?size=101", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
