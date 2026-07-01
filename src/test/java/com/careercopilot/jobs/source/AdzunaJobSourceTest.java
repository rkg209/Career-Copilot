package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.JobSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AdzunaJobSourceTest {

  private static final String BASE_URL = "https://adzuna.test";

  @Test
  void fetch_mapsResults() {
    String body =
        """
        {"results": [
          {"id": "a1", "title": "Data Engineer", "company": {"display_name": "Globex"},
           "location": {"display_name": "New York"}, "description": "pipelines",
           "redirect_url": "https://adzuna.test/a1", "created": "2026-06-20T00:00:00Z",
           "salary_min": 100000, "salary_max": 130000, "contract_time": "full_time"}
        ]}
        """;
    AdzunaJobSource source = source(body, "app-id", "app-key");

    List<NormalizedJob> jobs = source.fetch();

    assertThat(jobs).hasSize(1);
    NormalizedJob job = jobs.get(0);
    assertThat(job.source()).isEqualTo(JobSource.ADZUNA);
    assertThat(job.externalId()).isEqualTo("a1");
    assertThat(job.title()).isEqualTo("Data Engineer");
    assertThat(job.company()).isEqualTo("Globex");
    assertThat(job.location()).isEqualTo("New York");
    assertThat(job.employmentType()).isEqualTo("full_time");
    assertThat(job.salaryMin()).isEqualByComparingTo("100000");
    assertThat(job.salaryCurrency()).isEqualTo("USD");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_withoutCredentials_throws() {
    AdzunaJobSource source =
        new AdzunaJobSource(RestClient.builder(), BASE_URL, "", "", "us", 50, "USD");
    assertThatThrownBy(source::fetch).isInstanceOf(IllegalStateException.class);
  }

  private AdzunaJobSource source(String responseBody, String appId, String appKey) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/v1/api/jobs/us/search/1")))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    return new AdzunaJobSource(builder, BASE_URL, appId, appKey, "us", 50, "USD");
  }
}
