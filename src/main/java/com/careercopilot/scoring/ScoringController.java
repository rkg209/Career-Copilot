package com.careercopilot.scoring;

import com.careercopilot.scoring.dto.AtsGapReport;
import com.careercopilot.scoring.dto.ScoreRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scoring")
public class ScoringController {

  private final ScoringService scoringService;

  public ScoringController(ScoringService scoringService) {
    this.scoringService = scoringService;
  }

  @PostMapping("/score")
  public ResponseEntity<AtsGapReport> score(
      @Valid @RequestBody ScoreRequest request, Principal principal) {
    AtsGapReport report = scoringService.score(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(report);
  }

  @GetMapping("/results/{resumeVersionId}")
  public AtsGapReport latest(@PathVariable Long resumeVersionId, Principal principal) {
    return scoringService.latest(principal.getName(), resumeVersionId);
  }
}
