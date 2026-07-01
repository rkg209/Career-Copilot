package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobIngestionServiceTest {

  @Mock private JobPostingRepository repository;

  private NormalizedJob job(JobSource source, String externalId) {
    return new NormalizedJob(
        source,
        externalId,
        "Engineer",
        "Acme",
        "Remote",
        true,
        null,
        BigDecimal.valueOf(100000),
        null,
        "USD",
        List.of("Java"),
        List.of(),
        "full text body",
        "https://x",
        null,
        null,
        null);
  }

  @Test
  void ingestFrom_insertsNewAndUpdatesExisting() {
    JobSourceAdapter adapter =
        stub(
            JobSource.REMOTEOK,
            List.of(job(JobSource.REMOTEOK, "1"), job(JobSource.REMOTEOK, "2")));
    when(repository.findBySourceAndExternalId(JobSource.REMOTEOK, "1"))
        .thenReturn(Optional.empty());
    when(repository.findBySourceAndExternalId(JobSource.REMOTEOK, "2"))
        .thenReturn(Optional.of(new JobPosting()));
    JobIngestionService service =
        new JobIngestionService(List.of(adapter), repository, Set.of(JobSource.REMOTEOK));

    JobIngestionService.IngestionResult result = service.ingestFrom(adapter);

    assertThat(result.fetched()).isEqualTo(2);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    verify(repository, times(2)).save(any(JobPosting.class));
  }

  @Test
  void ingestAll_oneFailingAdapterDoesNotAbortOthers() {
    JobSourceAdapter failing = stubThrows(JobSource.REMOTEOK);
    JobSourceAdapter working = stub(JobSource.ADZUNA, List.of(job(JobSource.ADZUNA, "a1")));
    when(repository.findBySourceAndExternalId(eq(JobSource.ADZUNA), any()))
        .thenReturn(Optional.empty());
    JobIngestionService service =
        new JobIngestionService(
            List.of(failing, working), repository, Set.of(JobSource.REMOTEOK, JobSource.ADZUNA));

    service.ingestAll();

    verify(repository, times(1)).save(any(JobPosting.class));
  }

  private JobSourceAdapter stub(JobSource id, List<NormalizedJob> jobs) {
    return new JobSourceAdapter() {
      @Override
      public JobSource id() {
        return id;
      }

      @Override
      public List<NormalizedJob> fetch() {
        return jobs;
      }
    };
  }

  private JobSourceAdapter stubThrows(JobSource id) {
    return new JobSourceAdapter() {
      @Override
      public JobSource id() {
        return id;
      }

      @Override
      public List<NormalizedJob> fetch() {
        throw new RuntimeException("source down");
      }
    };
  }
}
