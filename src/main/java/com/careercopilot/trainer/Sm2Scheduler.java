package com.careercopilot.trainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Pure SM-2 spaced-repetition scheduler (NFR-7.1) — no Spring dependency, no persistence. Given the
 * current card state and a 0-5 quality grade, returns the next state. A grade below 3 is a lapse:
 * repetitions and interval reset, but the ease factor still moves (SM-2 never freezes EF).
 */
public final class Sm2Scheduler {

  public static final BigDecimal INITIAL_EF = new BigDecimal("2.50");
  static final BigDecimal MIN_EF = new BigDecimal("1.30");
  static final int FIRST_INTERVAL = 1;
  static final int SECOND_INTERVAL = 6;
  private static final int LAPSE_GRADE_THRESHOLD = 3;

  private Sm2Scheduler() {}

  public record Sm2State(BigDecimal easeFactor, int intervalDays, int repetitions, Instant dueAt) {}

  public static Sm2State update(
      BigDecimal easeFactor, int intervalDays, int repetitions, int grade, Instant now) {
    BigDecimal newEf = nextEaseFactor(easeFactor, grade);

    int newRepetitions;
    int newIntervalDays;
    if (grade < LAPSE_GRADE_THRESHOLD) {
      newRepetitions = 0;
      newIntervalDays = FIRST_INTERVAL;
    } else {
      newRepetitions = repetitions + 1;
      // Uses the ease factor as it stood entering this review, not the one just bumped by this
      // grade — matches standard SM-2 ordering and is what makes a 5,5,5 sequence yield 1/6/16.
      newIntervalDays = nextIntervalDays(newRepetitions, intervalDays, easeFactor);
    }

    Instant dueAt = now.plus(newIntervalDays, ChronoUnit.DAYS);
    return new Sm2State(newEf, newIntervalDays, newRepetitions, dueAt);
  }

  private static int nextIntervalDays(int repetitions, int previousIntervalDays, BigDecimal ef) {
    if (repetitions == 1) {
      return FIRST_INTERVAL;
    }
    if (repetitions == 2) {
      return SECOND_INTERVAL;
    }
    return BigDecimal.valueOf(previousIntervalDays)
        .multiply(ef)
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  private static BigDecimal nextEaseFactor(BigDecimal easeFactor, int grade) {
    double g = grade;
    double delta = 0.1 - (5 - g) * (0.08 + (5 - g) * 0.02);
    BigDecimal candidate = easeFactor.add(BigDecimal.valueOf(delta));
    BigDecimal floored = candidate.max(MIN_EF);
    return floored.setScale(2, RoundingMode.HALF_UP);
  }
}
