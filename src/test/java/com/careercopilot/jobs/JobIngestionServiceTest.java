package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.jobs.source.JobRelevanceFilter;
import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import com.careercopilot.jobs.source.QueryableJobSource;
import com.careercopilot.jobs.source.RoleQuery;
import com.careercopilot.jobs.source.RoleTagger;
import com.careercopilot.jobs.source.SkillExtractor;
import com.careercopilot.scoring.SkillAliasMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobIngestionServiceTest {

  @Mock private JobPostingRepository repository;

  // Empty targets → the relevance filter passes everything through (back-compat escape hatch).
  private final JobRelevanceFilter passThroughFilter =
      new JobRelevanceFilter(new JobTargetProperties());
  private final SkillExtractor skillExtractor = new SkillExtractor(new SkillAliasMap());
  // Empty role config → no aggregators queried, whole-board rows tagged with no roles.
  private final RoleProperties roleProperties = new RoleProperties();
  private final RoleTagger roleTagger = new RoleTagger(roleProperties);
  private final List<QueryableJobSource> noAggregators = List.of();

  private NormalizedJob job(JobSource source, String externalId) {
    return job(source, externalId, "Engineer", List.of("Java"), "full text body");
  }

  private NormalizedJob job(
      JobSource source, String externalId, String title, List<String> skills, String fullText) {
    return new NormalizedJob(
        source,
        externalId,
        title,
        "Acme",
        "Remote",
        true,
        null,
        BigDecimal.valueOf(100000),
        null,
        "USD",
        skills,
        List.of(),
        fullText,
        "https://x",
        null,
        null,
        null);
  }

  private JobRelevanceFilter filterFor(List<String> titleKeywords) {
    JobTargetProperties props = new JobTargetProperties();
    props.setTitleKeywords(titleKeywords);
    return new JobRelevanceFilter(props);
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
        new JobIngestionService(
            List.of(adapter),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.REMOTEOK),
            45);

    JobIngestionService.IngestionResult result = service.ingestFrom(adapter);

    assertThat(result.fetched()).isEqualTo(2);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.filteredOut()).isZero();
    assertThat(result.kept()).isEqualTo(2);
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
            List.of(failing, working),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.REMOTEOK, JobSource.ADZUNA),
            45);

    service.ingestAll();

    verify(repository, times(1)).save(any(JobPosting.class));
  }

  @Test
  void ingestFrom_dropsOffTargetPostings_andCountsFilteredOut() {
    JobSourceAdapter adapter =
        stub(
            JobSource.GREENHOUSE,
            List.of(
                job(JobSource.GREENHOUSE, "1", "Software Engineer", List.of("Java"), "backend"),
                job(JobSource.GREENHOUSE, "2", "Product Designer", List.of("Figma"), "design")));
    when(repository.findBySourceAndExternalId(eq(JobSource.GREENHOUSE), eq("1")))
        .thenReturn(Optional.empty());
    JobIngestionService service =
        new JobIngestionService(
            List.of(adapter),
            noAggregators,
            repository,
            filterFor(List.of("software engineer")),
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.GREENHOUSE),
            45);

    JobIngestionService.IngestionResult result = service.ingestFrom(adapter);

    assertThat(result.fetched()).isEqualTo(2);
    assertThat(result.filteredOut()).isEqualTo(1);
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.kept()).isEqualTo(1);
    verify(repository, times(1)).save(any(JobPosting.class)); // designer never persisted
  }

  @Test
  void ingestFrom_extractsSkillsOnlyWhenSourceProvidedNone() {
    NormalizedJob withSkills =
        job(JobSource.GREENHOUSE, "1", "ML Engineer", List.of("CustomSkill"), "python pytorch");
    NormalizedJob withoutSkills =
        job(JobSource.GREENHOUSE, "2", "ML Engineer", List.of(), "python and pytorch required");
    JobSourceAdapter adapter = stub(JobSource.GREENHOUSE, List.of(withSkills, withoutSkills));
    when(repository.findBySourceAndExternalId(eq(JobSource.GREENHOUSE), any()))
        .thenReturn(Optional.empty());
    JobIngestionService service =
        new JobIngestionService(
            List.of(adapter),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.GREENHOUSE),
            45);

    service.ingestFrom(adapter);

    ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
    verify(repository, times(2)).save(captor.capture());
    JobPosting kept = captor.getAllValues().get(0);
    JobPosting enriched = captor.getAllValues().get(1);
    assertThat(kept.getSkills()).containsExactly("CustomSkill"); // untouched
    assertThat(enriched.getSkills()).contains("python", "pytorch"); // extracted
  }

  @Test
  void ingestFrom_deactivatesPostingsMissingFromLatestFetch() {
    JobSourceAdapter adapter =
        stub(JobSource.GREENHOUSE, List.of(job(JobSource.GREENHOUSE, "still-open")));
    when(repository.findBySourceAndExternalId(JobSource.GREENHOUSE, "still-open"))
        .thenReturn(Optional.empty());
    when(repository.deactivateMissingExternalIds(eq(JobSource.GREENHOUSE), any())).thenReturn(3);
    JobIngestionService service =
        new JobIngestionService(
            List.of(adapter),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.GREENHOUSE),
            45);

    service.ingestFrom(adapter);

    ArgumentCaptor<Set<String>> seenIds = ArgumentCaptor.forClass(Set.class);
    verify(repository).deactivateMissingExternalIds(eq(JobSource.GREENHOUSE), seenIds.capture());
    assertThat(seenIds.getValue()).containsExactly("still-open");
  }

  @Test
  void ingestFrom_skipsDeactivationSweepWhenFetchIsEmpty() {
    JobSourceAdapter adapter = stub(JobSource.GREENHOUSE, List.of());
    JobIngestionService service =
        new JobIngestionService(
            List.of(adapter),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.GREENHOUSE),
            45);

    service.ingestFrom(adapter);

    verify(repository, never()).deactivateMissingExternalIds(any(), any());
  }

  @Test
  void deactivateStale_callsRepositoryWithCutoff() {
    JobIngestionService service =
        new JobIngestionService(
            List.of(),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(),
            30);
    when(repository.deactivateOlderThan(any())).thenReturn(4);

    Instant before = Instant.now().minusSeconds(30L * 86400);
    int deactivated = service.deactivateStale();

    assertThat(deactivated).isEqualTo(4);
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(repository).deactivateOlderThan(cutoff.capture());
    // cutoff ~= now - 30 days (allow a small window for execution time)
    assertThat(cutoff.getValue()).isBetween(before.minusSeconds(5), before.plusSeconds(5));
  }

  @Test
  void ingestAll_ignoresDisabledAdapters() {
    JobSourceAdapter disabled = stub(JobSource.ADZUNA, List.of(job(JobSource.ADZUNA, "x")));
    JobIngestionService service =
        new JobIngestionService(
            List.of(disabled),
            noAggregators,
            repository,
            passThroughFilter,
            skillExtractor,
            roleTagger,
            roleProperties,
            Set.of(JobSource.REMOTEOK),
            45);

    service.ingestAll();

    verify(repository, never()).save(any(JobPosting.class));
  }

  @Test
  void ingestFromQueryableSources_tagsResultsWithQueryRole() {
    RoleProperties props = new RoleProperties();
    RoleProperties.Role role = new RoleProperties.Role();
    role.setKeywords(List.of("ml"));
    props.setRoles(new java.util.LinkedHashMap<>(java.util.Map.of("ai-ml-engineer", role)));
    props.setSearchLocations(List.of("India"));

    QueryableJobSource aggregator =
        new QueryableJobSource() {
          @Override
          public JobSource id() {
            return JobSource.ADZUNA;
          }

          @Override
          public boolean isEnabled() {
            return true;
          }

          @Override
          public List<NormalizedJob> fetch(RoleQuery query) {
            return List.of(job(JobSource.ADZUNA, "x", "ML Engineer", List.of("Python"), "ml role"));
          }
        };
    when(repository.findBySourceAndExternalId(eq(JobSource.ADZUNA), eq("x")))
        .thenReturn(Optional.empty());
    JobIngestionService service =
        new JobIngestionService(
            List.of(),
            List.of(aggregator),
            repository,
            passThroughFilter,
            skillExtractor,
            new RoleTagger(props),
            props,
            Set.of(JobSource.ADZUNA),
            45);

    service.ingestFromQueryableSources();

    ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getMatchedRoles()).containsExactly("ai-ml-engineer");
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
