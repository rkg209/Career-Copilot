package com.careercopilot.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.shared.EmbeddingGateway;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ExperienceRetrievalIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired private ExperienceRepository experienceRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private EmbeddingGateway embeddingGateway;

  private UUID userA;
  private UUID userB;

  @BeforeEach
  void setUp() {
    experienceRepository.deleteAll();
    userA = user("retrieval-a@example.com");
    userB = user("retrieval-b@example.com");
  }

  @Test
  void findTopKByVector_returnsOnlyOwnersEntriesInCosineOrder() {
    // Unit vectors: entry "near" points along axis 0, "far" along axis 1, B's along axis 0 too.
    saveEntry(userA, "Near role", unitVector(0));
    saveEntry(userA, "Far role", unitVector(1));
    saveEntry(userB, "Other user's near role", unitVector(0));

    List<ExperienceEntry> results =
        experienceRepository.findTopKByVector(userA, unitVector(0), Limit.of(10));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).getRole()).isEqualTo("Near role");
    assertThat(results.get(1).getRole()).isEqualTo("Far role");
    assertThat(results).allMatch(e -> e.getUserId().equals(userA));
  }

  @Test
  void findTopKByVector_respectsLimit() {
    saveEntry(userA, "Role 1", unitVector(0));
    saveEntry(userA, "Role 2", unitVector(1));
    saveEntry(userA, "Role 3", unitVector(2));

    List<ExperienceEntry> results =
        experienceRepository.findTopKByVector(userA, unitVector(0), Limit.of(2));

    assertThat(results).hasSize(2);
  }

  @Test
  void findTopKByVector_skipsPendingEntries() {
    saveEntry(userA, "Embedded role", unitVector(0));
    ExperienceEntry pending = entry(userA, "Pending role");
    experienceRepository.save(pending);

    List<ExperienceEntry> results =
        experienceRepository.findTopKByVector(userA, unitVector(0), Limit.of(10));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getRole()).isEqualTo("Embedded role");
  }

  private UUID user(String email) {
    return userRepository
        .findByEmail(email)
        .orElseGet(
            () -> userRepository.save(new User(email, passwordEncoder.encode("password123"))))
        .getId();
  }

  private void saveEntry(UUID userId, String role, float[] embedding) {
    ExperienceEntry entry = entry(userId, role);
    entry.setEmbedding(embedding);
    entry.setTextHash(ExperienceText.sha256(ExperienceText.build(entry)));
    entry.setEmbeddedAt(Instant.now());
    experienceRepository.save(entry);
  }

  private static ExperienceEntry entry(UUID userId, String role) {
    ExperienceEntry entry = new ExperienceEntry(userId);
    entry.setRole(role);
    entry.setCompany("Acme");
    entry.setStartDate(LocalDate.of(2023, 1, 1));
    return entry;
  }

  private static float[] unitVector(int axis) {
    float[] vector = new float[768];
    vector[axis] = 1f;
    return vector;
  }
}
