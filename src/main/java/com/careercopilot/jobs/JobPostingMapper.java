package com.careercopilot.jobs;

import com.careercopilot.jobs.dto.JobPostingResponse;
import com.careercopilot.jobs.source.NormalizedJob;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Maps between {@link NormalizedJob}, the {@link JobPosting} entity, and API responses. */
final class JobPostingMapper {

  private JobPostingMapper() {}

  /** Copies every normalized field onto an entity (new or existing) and stamps ingestion time. */
  static void merge(JobPosting entity, NormalizedJob n) {
    entity.setSource(n.source());
    entity.setExternalId(n.externalId());
    entity.setTitle(n.title());
    entity.setCompany(n.company());
    entity.setLocation(n.location());
    entity.setRemote(n.remote());
    entity.setEmploymentType(n.employmentType());
    entity.setSalaryMin(n.salaryMin());
    entity.setSalaryMax(n.salaryMax());
    entity.setSalaryCurrency(n.salaryCurrency());
    entity.setSkills(n.skills() == null ? new ArrayList<>() : new ArrayList<>(n.skills()));
    entity.setResponsibilities(
        n.responsibilities() == null ? new ArrayList<>() : new ArrayList<>(n.responsibilities()));
    entity.setFullText(n.fullText());
    entity.setUrl(n.url());
    entity.setPostedAt(n.postedAt());
    entity.setExpiresAt(n.expiresAt());
    entity.setPastedByUserId(n.pastedByUserId());
    entity.setIngestedAt(Instant.now());
    entity.setActive(true);
  }

  static JobPosting toEntity(NormalizedJob n) {
    JobPosting entity = new JobPosting();
    merge(entity, n);
    return entity;
  }

  /** Full detail response (includes responsibilities + full text). */
  static JobPostingResponse toResponse(JobPosting j) {
    return new JobPostingResponse(
        j.getId(),
        j.getSource().name(),
        j.getExternalId(),
        j.getTitle(),
        j.getCompany(),
        j.getLocation(),
        j.isRemote(),
        j.getEmploymentType(),
        j.getSalaryMin(),
        j.getSalaryMax(),
        j.getSalaryCurrency(),
        List.copyOf(j.getSkills()),
        List.copyOf(j.getResponsibilities()),
        j.getFullText(),
        j.getUrl(),
        j.getPostedAt());
  }
}
