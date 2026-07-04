package com.careercopilot.tracker;

import com.careercopilot.shared.BadRequestException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed transitions: the linear happy-path chain {@code SAVED -> APPLIED -> PHONE_SCREEN ->
 * TECHNICAL_INTERVIEW -> FINAL_ROUND -> OFFER}, plus any non-terminal status may move to {@code
 * REJECTED} or {@code WITHDRAWN}. {@code OFFER}, {@code REJECTED}, {@code WITHDRAWN} are terminal
 * (no outbound transitions). Setting a status equal to the current one is always allowed (a no-op
 * update to other fields).
 */
final class ApplicationStatusMachine {

  private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS =
      new EnumMap<>(ApplicationStatus.class);

  static {
    TRANSITIONS.put(ApplicationStatus.SAVED, EnumSet.of(ApplicationStatus.APPLIED));
    TRANSITIONS.put(ApplicationStatus.APPLIED, EnumSet.of(ApplicationStatus.PHONE_SCREEN));
    TRANSITIONS.put(
        ApplicationStatus.PHONE_SCREEN, EnumSet.of(ApplicationStatus.TECHNICAL_INTERVIEW));
    TRANSITIONS.put(
        ApplicationStatus.TECHNICAL_INTERVIEW, EnumSet.of(ApplicationStatus.FINAL_ROUND));
    TRANSITIONS.put(ApplicationStatus.FINAL_ROUND, EnumSet.of(ApplicationStatus.OFFER));
    TRANSITIONS.put(ApplicationStatus.OFFER, EnumSet.noneOf(ApplicationStatus.class));
    TRANSITIONS.put(ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class));
    TRANSITIONS.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
  }

  private static final Set<ApplicationStatus> TERMINAL =
      EnumSet.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);

  private ApplicationStatusMachine() {}

  static void assertAllowed(ApplicationStatus current, ApplicationStatus requested) {
    if (current == requested) {
      return;
    }
    boolean withdrawOrReject =
        (requested == ApplicationStatus.REJECTED || requested == ApplicationStatus.WITHDRAWN)
            && !TERMINAL.contains(current);
    boolean onHappyPath = TRANSITIONS.get(current).contains(requested);
    if (!withdrawOrReject && !onHappyPath) {
      throw new BadRequestException("Invalid status transition: " + current + " -> " + requested);
    }
  }
}
