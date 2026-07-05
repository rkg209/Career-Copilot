package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.JobSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AshbyJobSourceTest {

  private static final String BASE_URL = "https://ashby.test";

  @Test
  void fetch_mapsJobs() {
    String body =
        """
        {"jobs": [
          {"id": "abc", "title": "Machine Learning Engineer", "location": "Bengaluru",
           "isRemote": false, "employmentType": "FullTime",
           "descriptionPlain": "Build ML systems with Python and PyTorch.",
           "jobUrl": "https://ashby.test/acme/abc",
           "organizationName": "Acme Inc", "publishedAt": "2026-06-20T00:00:00Z"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/posting-api/job-board/acme")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    AshbyJobSource source = new AshbyJobSource(builder, BASE_URL, List.of("acme"));

    List<NormalizedJob> result = source.fetch();

    assertThat(result).hasSize(1);
    NormalizedJob job = result.get(0);
    assertThat(job.source()).isEqualTo(JobSource.ASHBY);
    assertThat(job.externalId()).isEqualTo("acme:abc");
    assertThat(job.title()).isEqualTo("Machine Learning Engineer");
    assertThat(job.company()).isEqualTo("Acme Inc");
    assertThat(job.location()).isEqualTo("Bengaluru");
    assertThat(job.remote()).isFalse();
    assertThat(job.employmentType()).isEqualTo("FullTime");
    assertThat(job.fullText()).contains("PyTorch");
    assertThat(job.url()).isEqualTo("https://ashby.test/acme/abc");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_remoteFlagFromIsRemote() {
    String body =
        """
        {"jobs": [
          {"id": "r1", "title": "Backend Engineer", "location": "Anywhere",
           "isRemote": true, "jobUrl": "https://ashby.test/acme/r1"}
        ]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/posting-api/job-board/acme")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    AshbyJobSource source = new AshbyJobSource(builder, BASE_URL, List.of("acme"));

    NormalizedJob job = source.fetch().get(0);

    assertThat(job.remote()).isTrue();
    assertThat(job.company()).isEqualTo("acme"); // no organizationName -> token fallback
    assertThat(job.fullText()).isEqualTo("Backend Engineer"); // no description -> title fallback
    assertThat(job.postedAt()).isNull(); // no publishedAt
  }

  @Test
  void fetch_oneBoardFailing_doesNotAbortOthers() {
    String ok =
        """
        {"jobs": [{"id": "1", "title": "AI Engineer", "location": "Remote",
                   "jobUrl": "https://ashby.test/good/1"}]}
        """;
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/posting-api/job-board/bad")))
        .andRespond(withServerError());
    server
        .expect(requestTo(containsString("/posting-api/job-board/good")))
        .andRespond(withSuccess(ok, MediaType.APPLICATION_JSON));
    AshbyJobSource source = new AshbyJobSource(builder, BASE_URL, List.of("bad", "good"));

    List<NormalizedJob> result = source.fetch();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).externalId()).isEqualTo("good:1");
  }

  @Test
  void fetch_blankCompanyList_returnsEmpty() {
    AshbyJobSource source = new AshbyJobSource(RestClient.builder(), BASE_URL, List.of(""));
    assertThat(source.fetch()).isEmpty();
  }
}
