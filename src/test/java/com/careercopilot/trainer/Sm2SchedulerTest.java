package com.careercopilot.trainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class Sm2SchedulerTest {

  private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

  @Test
  void update_perfectGradeSequence_producesKnownIntervalsAndEf() {
    Sm2Scheduler.Sm2State s1 = Sm2Scheduler.update(Sm2Scheduler.INITIAL_EF, 0, 0, 5, NOW);
    assertThat(s1.intervalDays()).isEqualTo(1);
    assertThat(s1.repetitions()).isEqualTo(1);
    assertThat(s1.easeFactor()).isEqualTo(new BigDecimal("2.60"));
    assertThat(s1.dueAt()).isEqualTo(NOW.plus(1, ChronoUnit.DAYS));

    Sm2Scheduler.Sm2State s2 =
        Sm2Scheduler.update(s1.easeFactor(), s1.intervalDays(), s1.repetitions(), 5, NOW);
    assertThat(s2.intervalDays()).isEqualTo(6);
    assertThat(s2.repetitions()).isEqualTo(2);
    assertThat(s2.easeFactor()).isEqualTo(new BigDecimal("2.70"));

    Sm2Scheduler.Sm2State s3 =
        Sm2Scheduler.update(s2.easeFactor(), s2.intervalDays(), s2.repetitions(), 5, NOW);
    assertThat(s3.intervalDays()).isEqualTo(16);
    assertThat(s3.repetitions()).isEqualTo(3);
    assertThat(s3.easeFactor()).isEqualTo(new BigDecimal("2.80"));
  }

  @Test
  void update_lowGrade_lapsesRepetitionsAndIntervalButStillMovesEf() {
    // Simulate arriving at a strong card (EF 2.80, interval 16, reps 3) then failing it.
    Sm2Scheduler.Sm2State lapse = Sm2Scheduler.update(new BigDecimal("2.80"), 16, 3, 1, NOW);
    assertThat(lapse.repetitions()).isEqualTo(0);
    assertThat(lapse.intervalDays()).isEqualTo(1);
    assertThat(lapse.easeFactor()).isLessThan(new BigDecimal("2.80"));
    assertThat(lapse.dueAt()).isEqualTo(NOW.plus(1, ChronoUnit.DAYS));
  }

  @Test
  void update_repeatedZeroGrades_floorsEaseFactorAtMinimum() {
    BigDecimal ef = Sm2Scheduler.INITIAL_EF;
    int interval = 0;
    int repetitions = 0;
    for (int i = 0; i < 30; i++) {
      Sm2Scheduler.Sm2State state = Sm2Scheduler.update(ef, interval, repetitions, 0, NOW);
      ef = state.easeFactor();
      interval = state.intervalDays();
      repetitions = state.repetitions();
    }
    assertThat(ef).isEqualTo(new BigDecimal("1.30"));
    assertThat(repetitions).isEqualTo(0);
    assertThat(interval).isEqualTo(1);
  }

  @Test
  void update_gradeThree_isNotALapse_andIncrementsRepetitions() {
    Sm2Scheduler.Sm2State state = Sm2Scheduler.update(Sm2Scheduler.INITIAL_EF, 0, 0, 3, NOW);
    assertThat(state.repetitions()).isEqualTo(1);
    assertThat(state.intervalDays()).isEqualTo(1);
  }
}
