package com.careercopilot.shared.guardrail;

import com.careercopilot.shared.PiiRedactionService;
import org.springframework.stereotype.Component;

/**
 * Guardrail wrapper around {@link PiiRedactionService} applied inside LlmGateway to both outgoing
 * prompts and incoming responses before anything is sent to the provider or written to logs.
 */
@Component
public class PiiRedactionAdvisor {

  private final PiiRedactionService piiRedactionService;

  public PiiRedactionAdvisor(PiiRedactionService piiRedactionService) {
    this.piiRedactionService = piiRedactionService;
  }

  public String redact(String text) {
    return piiRedactionService.redact(text);
  }
}
