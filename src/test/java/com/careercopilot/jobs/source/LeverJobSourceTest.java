package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.JobSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LeverJobSourceTest {

  private static final String BASE_URL = "https://lever.test";

  @Test
  void fetch_mapsPostings() {
    String body =
        """
        [
          {"id": "abc-1", "text": "Staff Engineer",
           "categories": {"location": "Bengaluru, India", "commitment": "Full-time"},
           "descriptionPlain": "own the platform", "hostedUrl": "https://lever.test/acme/abc-1",
           "createdAt": 1718841600000}
        ]
        """;
    LeverJobSource source = source(body);

    List<NormalizedJob> result = source.fetch();

    assertThat(result).hasSize(1);
    NormalizedJob job = result.get(0);
    assertThat(job.source()).isEqualTo(JobSource.LEVER);
    assertThat(job.externalId()).isEqualTo("acme:abc-1");
    assertThat(job.title()).isEqualTo("Staff Engineer");
    assertThat(job.company()).isEqualTo("acme");
    assertThat(job.location()).isEqualTo("Bengaluru, India");
    assertThat(job.employmentType()).isEqualTo("Full-time");
    assertThat(job.fullText()).isEqualTo("own the platform");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_errorPayloadNotArray_returnsEmpty() {
    LeverJobSource source = source("{\"ok\": false, \"error\": \"not found\"}");
    assertThat(source.fetch()).isEmpty();
  }

  private LeverJobSource source(String responseBody) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/v0/postings/acme")))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    return new LeverJobSource(builder, BASE_URL, List.of("acme"));
  }
}
