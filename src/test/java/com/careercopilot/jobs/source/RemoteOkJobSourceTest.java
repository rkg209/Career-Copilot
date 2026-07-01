package com.careercopilot.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.jobs.JobSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteOkJobSourceTest {

  private static final String BASE_URL = "https://remoteok.test";

  @Test
  void fetch_skipsLegalNotice_andMapsJob() {
    String body =
        """
        [
          {"legal": "In exchange for using this API you agree..."},
          {"id": "12345", "position": "Backend Engineer", "company": "Acme",
           "location": "Remote", "tags": ["Java", "Spring"], "description": "Build things",
           "url": "https://remoteok.test/l/12345", "epoch": 1700000000,
           "salary_min": 120000, "salary_max": 160000}
        ]
        """;
    RemoteOkJobSource source = source(body);

    List<NormalizedJob> jobs = source.fetch();

    assertThat(jobs).hasSize(1);
    NormalizedJob job = jobs.get(0);
    assertThat(job.source()).isEqualTo(JobSource.REMOTEOK);
    assertThat(job.externalId()).isEqualTo("12345");
    assertThat(job.title()).isEqualTo("Backend Engineer");
    assertThat(job.company()).isEqualTo("Acme");
    assertThat(job.remote()).isTrue();
    assertThat(job.skills()).containsExactly("Java", "Spring");
    assertThat(job.salaryMin()).isEqualByComparingTo("120000");
    assertThat(job.salaryCurrency()).isEqualTo("USD");
    assertThat(job.postedAt()).isNotNull();
  }

  @Test
  void fetch_missingSalary_leavesSalaryNull() {
    String body =
        """
        [
          {"id": "9", "position": "SRE", "company": "Globex", "tags": [], "description": "ops"}
        ]
        """;
    RemoteOkJobSource source = source(body);

    NormalizedJob job = source.fetch().get(0);

    assertThat(job.salaryMin()).isNull();
    assertThat(job.salaryMax()).isNull();
    assertThat(job.salaryCurrency()).isNull();
  }

  private RemoteOkJobSource source(String responseBody) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/api"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    return new RemoteOkJobSource(builder, BASE_URL);
  }
}
