package com.careercopilot.intel;

import com.careercopilot.intel.dto.CompanyIntelDetail;
import com.careercopilot.intel.dto.CompanyIntelSummary;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.tracker.InterviewRoundType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregation over published {@link CompanyIntelEntry} rows — never reads staged rows.
 */
@Service
@Transactional(readOnly = true)
public class IntelService {

  private final CompanyIntelRepository companyIntelRepository;

  public IntelService(CompanyIntelRepository companyIntelRepository) {
    this.companyIntelRepository = companyIntelRepository;
  }

  public PageResponse<CompanyIntelSummary> browseCompanies(Pageable pageable) {
    Page<String> companies = companyIntelRepository.findDistinctPublishedCompanies(pageable);
    // Single batched fetch for the whole page instead of one query per company (was N+1).
    List<CompanyIntelEntry> pageEntries =
        companyIntelRepository.findByCompanyInAndPublishedTrue(companies.getContent());
    Map<String, List<CompanyIntelEntry>> byCompany = new LinkedHashMap<>();
    for (CompanyIntelEntry entry : pageEntries) {
      byCompany.computeIfAbsent(entry.getCompany(), k -> new ArrayList<>()).add(entry);
    }
    List<CompanyIntelSummary> content =
        companies.getContent().stream()
            .map(company -> summarize(company, byCompany.getOrDefault(company, List.of())))
            .toList();
    return PageResponse.of(companies, content);
  }

  public CompanyIntelDetail byCompany(String company) {
    List<CompanyIntelEntry> entries = companyIntelRepository.findByCompanyAndPublishedTrue(company);
    if (entries.isEmpty()) {
      throw new ResourceNotFoundException("No published intel for company: " + company);
    }

    Map<InterviewRoundType, List<CompanyIntelEntry>> byRoundType = new LinkedHashMap<>();
    for (CompanyIntelEntry entry : entries) {
      byRoundType.computeIfAbsent(entry.getRoundType(), k -> new ArrayList<>()).add(entry);
    }

    List<CompanyIntelDetail.RoundSummary> rounds =
        byRoundType.entrySet().stream().map(e -> toRoundSummary(e.getKey(), e.getValue())).toList();
    return new CompanyIntelDetail(company, rounds);
  }

  private CompanyIntelSummary summarize(String company, List<CompanyIntelEntry> entries) {
    List<InterviewRoundType> roundTypes =
        entries.stream().map(CompanyIntelEntry::getRoundType).distinct().toList();
    OptionalDouble average = averageDifficulty(entries);
    Double avgDifficulty =
        average.isPresent() ? Math.round(average.getAsDouble() * 10.0) / 10.0 : null;
    return new CompanyIntelSummary(company, roundTypes, entries.size(), avgDifficulty);
  }

  private CompanyIntelDetail.RoundSummary toRoundSummary(
      InterviewRoundType roundType, List<CompanyIntelEntry> entries) {
    String roleCategory =
        entries.stream()
            .map(CompanyIntelEntry::getRoleCategory)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    List<String> questions =
        entries.stream().flatMap(e -> e.getQuestions().stream()).distinct().toList();
    String processNotes =
        entries.stream()
            .map(CompanyIntelEntry::getProcessNotes)
            .filter(n -> n != null && !n.isBlank())
            .distinct()
            .reduce((a, b) -> a + "\n\n" + b)
            .orElse(null);
    OptionalDouble average = averageDifficulty(entries);
    Integer difficulty = average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    return new CompanyIntelDetail.RoundSummary(
        roundType, roleCategory, questions, processNotes, difficulty, entries.size());
  }

  private OptionalDouble averageDifficulty(List<CompanyIntelEntry> entries) {
    return entries.stream()
        .map(CompanyIntelEntry::getDifficulty)
        .filter(Objects::nonNull)
        .mapToInt(Integer::intValue)
        .average();
  }
}
