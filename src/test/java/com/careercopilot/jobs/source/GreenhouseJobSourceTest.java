package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.JobSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GreenhouseJobSourceTest {

  private static final String BASE_URL = "https://greenhouse.test";

  @Test
  void fetch_mapsJobs_withBoardName() {
    String jobs =
        """
        {"jobs": [
          {"id": 1, "title": "AI Engineer", "location": {"name": "Remote - India"},
           "content": "<p>build ml systems</p>", "absolute_url": "https://greenhouse.test/acme/1",
           "updated_at": "2026-06-20T00:00:00Z"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(endsWith("/v1/boards/acme")))
        .andRespond(withSuccess("{\"name\": \"Acme\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(containsString("/v1/boards/acme/jobs")))
        .andRespond(withSuccess(jobs, MediaType.APPLICATION_JSON));
    GreenhouseJobSource source = new GreenhouseJobSource(builder, BASE_URL, List.of("acme"));

    List<NormalizedJob> result = source.fetch();

    assertThat(result).hasSize(1);
    NormalizedJob job = result.get(0);
    assertThat(job.source()).isEqualTo(JobSource.GREENHOUSE);
    assertThat(job.externalId()).isEqualTo("acme:1");
    assertThat(job.title()).isEqualTo("AI Engineer");
    assertThat(job.company()).isEqualTo("Acme");
    assertThat(job.location()).isEqualTo("Remote - India");
    assertThat(job.remote()).isTrue();
    assertThat(job.fullText()).contains("ml systems");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_boardNameLookupFails_fallsBackToToken() {
    String jobs =
        """
        {"jobs": [
          {"id": 9, "title": "Backend Engineer", "location": {"name": "Bengaluru, India"},
           "absolute_url": "https://greenhouse.test/acme/9"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(endsWith("/v1/boards/acme")))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    server
        .expect(requestTo(containsString("/v1/boards/acme/jobs")))
        .andRespond(withSuccess(jobs, MediaType.APPLICATION_JSON));
    GreenhouseJobSource source = new GreenhouseJobSource(builder, BASE_URL, List.of("acme"));

    NormalizedJob job = source.fetch().get(0);

    assertThat(job.company()).isEqualTo("acme"); // fallback to token
    assertThat(job.location()).contains("India");
    assertThat(job.remote()).isFalse();
    assertThat(job.fullText()).isEqualTo("Backend Engineer"); // no content -> title fallback
  }

  @Test
  void fetch_blankCompanyList_returnsEmpty() {
    GreenhouseJobSource source =
        new GreenhouseJobSource(RestClient.builder(), BASE_URL, List.of(""));
    assertThat(source.fetch()).isEmpty();
  }
}
