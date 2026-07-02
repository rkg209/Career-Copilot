package com.careercopilot.tailoring.render;

import com.careercopilot.shared.BadRequestException;

/** Unknown template id. Mapped to 400 via {@link BadRequestException}. */
public class TemplateNotFoundException extends BadRequestException {

  public TemplateNotFoundException(String templateId) {
    super("Unknown template id: " + templateId);
  }
}
