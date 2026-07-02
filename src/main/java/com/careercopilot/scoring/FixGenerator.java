package com.careercopilot.scoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Template-based fix suggestions (no LLM). Ordering: required near-misses first (cheapest wins —
 * the experience is already there, just not named), then remaining missing required, then missing
 * preferred.
 */
@Component
public class FixGenerator {

  public record Fix(String skill, String suggestedBullet, int priority) {}

  public List<Fix> build(
      List<String> missingRequired,
      List<String> missingPreferred,
      List<SemanticAugmenter.NearMiss> nearMisses) {
    Map<String, SemanticAugmenter.NearMiss> nearMissBySkill = new LinkedHashMap<>();
    for (SemanticAugmenter.NearMiss nearMiss : nearMisses) {
      nearMissBySkill.putIfAbsent(nearMiss.skill(), nearMiss);
    }

    List<Fix> fixes = new ArrayList<>();
    int priority = 1;
    for (SemanticAugmenter.NearMiss nearMiss : nearMissBySkill.values()) {
      fixes.add(
          new Fix(
              nearMiss.skill(),
              "Your resume already says \"%s\" — name \"%s\" explicitly in that bullet or in your skills section."
                  .formatted(truncate(nearMiss.suggestedPhrase()), nearMiss.skill()),
              priority++));
    }
    for (String skill : missingRequired) {
      if (nearMissBySkill.containsKey(skill)) {
        continue;
      }
      fixes.add(
          new Fix(
              skill,
              "Required by this JD: add a bullet demonstrating real experience with \"%s\", or list it in your skills section if you have used it."
                  .formatted(skill),
              priority++));
    }
    for (String skill : missingPreferred) {
      fixes.add(
          new Fix(
              skill,
              "Nice-to-have: mention \"%s\" if you have genuine exposure to it — it strengthens the match but is not required."
                  .formatted(skill),
              priority++));
    }
    return fixes;
  }

  private static String truncate(String phrase) {
    return phrase.length() <= 80 ? phrase : phrase.substring(0, 77) + "...";
  }
}
