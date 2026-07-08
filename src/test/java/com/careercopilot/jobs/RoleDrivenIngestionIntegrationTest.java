package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.source.JobDescriptionEnricher;
import com.careercopilot.jobs.source.JobRelevanceFilter;
import com.careercopilot.jobs.source.JobSourceAdapter;
import com.careercopilot.jobs.source.NormalizedJob;
import com.careercopilot.jobs.source.QueryableJobSource;
import com.careercopilot.jobs.source.RoleQuery;
import com.careercopilot.jobs.source.RoleTagger;
import com.careercopilot.jobs.source.SkillExtractor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end role tagging against Testcontainers: aggregator results carry the query role;
 * whole-board results are keyword-tagged with every matching role; re-ingest unions the roles.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "jobs.search-locations=India",
      "jobs.roles.ai-ml-engineer.label=AI / ML Engineer",
      "jobs.roles.ai-ml-engineer.keywords=machine learning",
      "jobs.roles.software-engineer.label=Software Engineer",
      "jobs.roles.software-engineer.keywords=software engineer",
      "jobs.enrichment.enabled=false"
    })
class RoleDrivenIngestionIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private JobPostingRepository repository;
  @Autowired private JobRelevanceFilter relevanceFilter;
  @Autowired private SkillExtractor skillExtractor;
  @Autowired private JobDescriptionEnricher disabledEnricher;
  @Autowired private RoleTagger roleTagger;
  @Autowired private RoleProperties roleProperties;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private JobIngestionService service(
      List<JobSourceAdapter> wholeBoard, List<QueryableJobSource> aggregators) {
    return new JobIngestionService(
        wholeBoard,
        aggregators,
        repository,
        relevanceFilter,
        skillExtractor,
        disabledEnricher,
        roleTagger,
        roleProperties,
        Set.of(JobSource.JOOBLE, JobSource.GREENHOUSE),
        45);
  }

  private NormalizedJob job(JobSource source, String extId, String title) {
    return new NormalizedJob(
        source,
        extId,
        title,
        "Acme",
        "Bengaluru, India",
        false,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        title,
        "https://x/" + extId,
        null,
        null,
        null);
  }

  @Test
  void tagsAggregatorByQueryRole_andWholeBoardByKeyword_unioningOnReingest() {
    // Aggregator (JOOBLE) returns the ML job only for the ai-ml-engineer query.
    QueryableJobSource aggregator =
        new QueryableJobSource() {
          @Override
          public JobSource id() {
            return JobSource.JOOBLE;
          }

          @Override
          public boolean isEnabled() {
            return true;
          }

          @Override
          public List<NormalizedJob> fetch(RoleQuery query) {
            return query.roleKey().equals("ai-ml-engineer")
                ? List.of(job(JobSource.JOOBLE, "j1", "Machine Learning Engineer"))
                : List.of();
          }
        };
    // Whole-board (GREENHOUSE) posting matches BOTH role keyword sets.
    JobSourceAdapter wholeBoard =
        new JobSourceAdapter() {
          @Override
          public JobSource id() {
            return JobSource.GREENHOUSE;
          }

          @Override
          public List<NormalizedJob> fetch() {
            return List.of(job(JobSource.GREENHOUSE, "g1", "Machine Learning Software Engineer"));
          }
        };

    JobIngestionService service = service(List.of(wholeBoard), List.of(aggregator));
    service.ingestAll();

    assertThat(
            repository
                .findBySourceAndExternalId(JobSource.JOOBLE, "j1")
                .orElseThrow()
                .getMatchedRoles())
        .containsExactly("ai-ml-engineer");
    assertThat(
            repository
                .findBySourceAndExternalId(JobSource.GREENHOUSE, "g1")
                .orElseThrow()
                .getMatchedRoles())
        .containsExactlyInAnyOrder("ai-ml-engineer", "software-engineer");

    // Re-ingest: roles are unioned (deduped), not duplicated.
    service.ingestAll();
    assertThat(
            repository
                .findBySourceAndExternalId(JobSource.GREENHOUSE, "g1")
                .orElseThrow()
                .getMatchedRoles())
        .containsExactlyInAnyOrder("ai-ml-engineer", "software-engineer");
    assertThat(repository.count()).isEqualTo(2);
  }
}
