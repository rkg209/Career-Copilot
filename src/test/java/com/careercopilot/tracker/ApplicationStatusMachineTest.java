package com.careercopilot.tracker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careercopilot.shared.BadRequestException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApplicationStatusMachineTest {

  @Test
  void happyPath_everyLinearStepAllowed() {
    assertThatCode(
            () -> {
              ApplicationStatusMachine.assertAllowed(
                  ApplicationStatus.SAVED, ApplicationStatus.APPLIED);
              ApplicationStatusMachine.assertAllowed(
                  ApplicationStatus.APPLIED, ApplicationStatus.PHONE_SCREEN);
              ApplicationStatusMachine.assertAllowed(
                  ApplicationStatus.PHONE_SCREEN, ApplicationStatus.TECHNICAL_INTERVIEW);
              ApplicationStatusMachine.assertAllowed(
                  ApplicationStatus.TECHNICAL_INTERVIEW, ApplicationStatus.FINAL_ROUND);
              ApplicationStatusMachine.assertAllowed(
                  ApplicationStatus.FINAL_ROUND, ApplicationStatus.OFFER);
            })
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = ApplicationStatus.class,
      names = {"OFFER", "REJECTED", "WITHDRAWN"},
      mode = EnumSource.Mode.EXCLUDE)
  void anyNonTerminalStatus_canMoveToRejected(ApplicationStatus current) {
    assertThatCode(
            () -> ApplicationStatusMachine.assertAllowed(current, ApplicationStatus.REJECTED))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = ApplicationStatus.class,
      names = {"OFFER", "REJECTED", "WITHDRAWN"},
      mode = EnumSource.Mode.EXCLUDE)
  void anyNonTerminalStatus_canMoveToWithdrawn(ApplicationStatus current) {
    assertThatCode(
            () -> ApplicationStatusMachine.assertAllowed(current, ApplicationStatus.WITHDRAWN))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(ApplicationStatus.class)
  void sameStatus_isAlwaysANoOp(ApplicationStatus status) {
    assertThatCode(() -> ApplicationStatusMachine.assertAllowed(status, status))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = ApplicationStatus.class,
      names = {"OFFER", "REJECTED", "WITHDRAWN"})
  void terminalStatuses_haveNoOutboundTransitions(ApplicationStatus terminal) {
    Set<ApplicationStatus> others = EnumSet.allOf(ApplicationStatus.class);
    others.remove(terminal);
    for (ApplicationStatus target : others) {
      assertThatThrownBy(() -> ApplicationStatusMachine.assertAllowed(terminal, target))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Test
  void skippingAStage_isRejected() {
    assertThatThrownBy(
            () ->
                ApplicationStatusMachine.assertAllowed(
                    ApplicationStatus.SAVED, ApplicationStatus.TECHNICAL_INTERVIEW))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void movingBackward_isRejected() {
    assertThatThrownBy(
            () ->
                ApplicationStatusMachine.assertAllowed(
                    ApplicationStatus.FINAL_ROUND, ApplicationStatus.APPLIED))
        .isInstanceOf(BadRequestException.class);
  }
}
