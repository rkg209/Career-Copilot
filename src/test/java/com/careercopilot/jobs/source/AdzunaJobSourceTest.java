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

class AdzunaJobSourceTest {

  private static final String BASE_URL = "https://adzuna.test";
  private static final RoleQuery QUERY =
      new RoleQuery("data-scientist", List.of("data engineer", "data scientist"), "India");

  @Test
  void fetch_mapsResults_usingDisplayNameFallback() {
    String body =
        """
        {"results": [
          {"id": "a1", "title": "Data Engineer", "company": {"display_name": "Globex"},
           "location": {"display_name": "Bengaluru"}, "description": "pipelines",
           "redirect_url": "https://adzuna.test/a1", "created": "2026-06-20T00:00:00Z",
           "salary_min": 100000, "salary_max": 130000, "contract_time": "full_time"}
        ]}
        """;
    AdzunaJobSource source = source(body, "in");

    List<NormalizedJob> jobs = source.fetch(QUERY);

    assertThat(jobs).hasSize(1);
    NormalizedJob job = jobs.get(0);
    assertThat(job.source()).isEqualTo(JobSource.ADZUNA);
    assertThat(job.externalId()).isEqualTo("a1");
    assertThat(job.title()).isEqualTo("Data Engineer");
    assertThat(job.company()).isEqualTo("Globex");
    assertThat(job.location()).isEqualTo("Bengaluru");
    assertThat(job.employmentType()).isEqualTo("full_time");
    assertThat(job.salaryMin()).isEqualByComparingTo("100000");
    assertThat(job.salaryCurrency()).isEqualTo("INR");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_buildsLocationFromAreaArray_includingCountry() {
    String body =
        """
        {"results": [
          {"id": "b2", "title": "AI Engineer", "company": {"display_name": "Acme India"},
           "location": {"display_name": "Bengaluru, Karnataka",
                        "area": ["India", "Karnataka", "Bengaluru"]},
           "description": "ml", "redirect_url": "https://adzuna.test/b2"}
        ]}
        """;
    AdzunaJobSource source = source(body, "in");

    NormalizedJob job = source.fetch(QUERY).get(0);

    assertThat(job.location()).isEqualTo("India, Karnataka, Bengaluru");
  }

  @Test
  void withoutCredentials_isDisabled_andFetchReturnsEmpty() {
    AdzunaJobSource source =
        new AdzunaJobSource(RestClient.builder(), BASE_URL, "", "", "in", 50, 1, "INR");
    assertThat(source.isEnabled()).isFalse();
    assertThat(source.fetch(QUERY)).isEmpty();
  }

  private AdzunaJobSource source(String responseBody, String country) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/v1/api/jobs/" + country + "/search/1")))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    return new AdzunaJobSource(builder, BASE_URL, "app-id", "app-key", country, 50, 1, "INR");
  }
}
