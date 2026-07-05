package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "jobs.roles.ai-ml-engineer.label=AI / ML Engineer",
      "jobs.roles.ai-ml-engineer.keywords=machine learning",
      "jobs.roles.quant.label=Quant / Trading",
      "jobs.roles.quant.keywords=quant"
    })
class JobRolesEndpointIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void roles_returnsConfiguredPresets_publicly() {
    ResponseEntity<JsonNode> res = restTemplate.getForEntity("/api/v1/jobs/roles", JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode roles = res.getBody().get("roles");
    assertThat(roles).hasSize(2);
    java.util.Map<String, String> byKey = new java.util.HashMap<>();
    roles.forEach(r -> byKey.put(r.get("key").asText(), r.get("label").asText()));
    assertThat(byKey).containsEntry("ai-ml-engineer", "AI / ML Engineer").containsKey("quant");
  }
}
