package com.careercopilot.shared;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tiered model configuration (CLAUDE.md LLM rules): model selection, token budget, and temperature
 * per {@link LlmTask}. Model names are never hardcoded in Java — they come from application.yml /
 * environment variables only.
 */
@ConfigurationProperties(prefix = "llm")
public class LlmTaskConfig {

  private Map<LlmTask, TierSettings> tiers = new EnumMap<>(LlmTask.class);

  public Map<LlmTask, TierSettings> getTiers() {
    return tiers;
  }

  public void setTiers(Map<LlmTask, TierSettings> tiers) {
    this.tiers = tiers;
  }

  public TierSettings require(LlmTask task) {
    TierSettings settings = tiers.get(task);
    if (settings == null) {
      throw new IllegalStateException("No llm.tiers config for task " + task);
    }
    return settings;
  }

  public static class TierSettings {
    private String model;
    private int maxTokens;
    private double temperature;

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public int getMaxTokens() {
      return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
      this.maxTokens = maxTokens;
    }

    public double getTemperature() {
      return temperature;
    }

    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }
  }
}
