package com.careercopilot.metrics;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.metrics.dto.AtsImprovementReport;
import com.careercopilot.metrics.dto.TokenUsageReport;
import com.careercopilot.shared.ResourceNotFoundException;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

  private final AtsImprovementService atsImprovementService;
  private final TokenUsageReportService tokenUsageReportService;
  private final UserRepository userRepository;

  public MetricsController(
      AtsImprovementService atsImprovementService,
      TokenUsageReportService tokenUsageReportService,
      UserRepository userRepository) {
    this.atsImprovementService = atsImprovementService;
    this.tokenUsageReportService = tokenUsageReportService;
    this.userRepository = userRepository;
  }

  @GetMapping("/ats-improvement")
  public AtsImprovementReport atsImprovement() {
    return atsImprovementService.report();
  }

  @GetMapping("/tokens")
  public TokenUsageReport tokens(Principal principal) {
    User user = resolveUser(principal.getName());
    return tokenUsageReportService.report(user.getId());
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }
}
