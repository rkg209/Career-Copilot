package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.JobSource;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JoobleJobSourceTest {

  private static final String BASE_URL = "https://jooble.test";
  private static final RoleQuery QUERY =
      new RoleQuery("ai-ml-engineer", List.of("machine learning", "ml engineer"), "India");

  @Test
  void fetch_postsKeywordsAndLocation_mapsJobs() {
    String body =
        """
        {"totalCount": 1, "jobs": [
          {"id": "j1", "title": "Machine Learning Engineer", "company": "Acme",
           "location": "Bengaluru, India", "snippet": "Build ML systems",
           "type": "Full-time", "link": "https://jooble.test/j1",
           "updated": "2026-06-25T00:00:00Z"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(Matchers.endsWith("/api/test-key")))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(Matchers.containsString("machine learning")))
        .andExpect(content().string(Matchers.containsString("India")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    JoobleJobSource source = new JoobleJobSource(builder, BASE_URL, "test-key");

    List<NormalizedJob> jobs = source.fetch(QUERY);

    assertThat(jobs).hasSize(1);
    NormalizedJob job = jobs.get(0);
    assertThat(job.source()).isEqualTo(JobSource.JOOBLE);
    assertThat(job.externalId()).isEqualTo("j1");
    assertThat(job.title()).isEqualTo("Machine Learning Engineer");
    assertThat(job.company()).isEqualTo("Acme");
    assertThat(job.location()).isEqualTo("Bengaluru, India");
    assertThat(job.fullText()).contains("Build ML systems");
    assertThat(job.url()).isEqualTo("https://jooble.test/j1");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_remoteFlagFromLocation() {
    String body =
        """
        {"jobs": [{"id": "r1", "title": "ML Engineer", "location": "Remote - India",
                   "snippet": "x", "link": "https://jooble.test/r1"}]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(Matchers.endsWith("/api/test-key")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    JoobleJobSource source = new JoobleJobSource(builder, BASE_URL, "test-key");

    assertThat(source.fetch(QUERY).get(0).remote()).isTrue();
  }

  @Test
  void withoutKey_isDisabled_andFetchReturnsEmpty() {
    JoobleJobSource source = new JoobleJobSource(RestClient.builder(), BASE_URL, "");
    assertThat(source.isEnabled()).isFalse();
    assertThat(source.fetch(QUERY)).isEmpty();
  }
}
