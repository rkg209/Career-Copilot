package com.careercopilot.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Serves the OpenAPI 3.1 document at {@code /api/v1/docs} (springdoc auto-generates the paths). */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI careerCopilotOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Career Copilot API")
                .version("v1")
                .description(
                    "Multi-user RAG-powered career copilot: job ingestion, profile/experience"
                        + " corpus, resume tailoring, ATS gap scoring, application tracking,"
                        + " company interview intel, and a mock-interview trainer."))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
