package com.careercopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerCopilotApplication {

  public static void main(String[] args) {
    SpringApplication.run(CareerCopilotApplication.class, args);
  }
}
