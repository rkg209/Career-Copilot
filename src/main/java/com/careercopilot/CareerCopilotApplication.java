package com.careercopilot;

import com.careercopilot.jobs.JobTargetProperties;
import com.careercopilot.jobs.RoleProperties;
import com.careercopilot.shared.LlmTaskConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  LlmTaskConfig.class,
  JobTargetProperties.class,
  RoleProperties.class
})
public class CareerCopilotApplication {

  public static void main(String[] args) {
    SpringApplication.run(CareerCopilotApplication.class, args);
  }
}
