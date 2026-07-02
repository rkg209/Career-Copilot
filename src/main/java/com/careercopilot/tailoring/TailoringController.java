package com.careercopilot.tailoring;

import com.careercopilot.shared.PageResponse;
import com.careercopilot.tailoring.dto.CoverLetterRequest;
import com.careercopilot.tailoring.dto.CoverLetterResponse;
import com.careercopilot.tailoring.dto.ResumeDetailResponse;
import com.careercopilot.tailoring.dto.ResumeResponse;
import com.careercopilot.tailoring.dto.TailorRequest;
import com.careercopilot.tailoring.dto.TemplateResponse;
import com.careercopilot.tailoring.render.TemplateRegistry;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tailoring")
public class TailoringController {

  private final TailoringService tailoringService;
  private final CoverLetterService coverLetterService;
  private final TemplateRegistry templateRegistry;

  public TailoringController(
      TailoringService tailoringService,
      CoverLetterService coverLetterService,
      TemplateRegistry templateRegistry) {
    this.tailoringService = tailoringService;
    this.coverLetterService = coverLetterService;
    this.templateRegistry = templateRegistry;
  }

  @PostMapping("/tailor")
  public ResponseEntity<ResumeResponse> tailor(
      @Valid @RequestBody TailorRequest request, Principal principal) {
    ResumeResponse created = tailoringService.tailor(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/resumes")
  public PageResponse<ResumeResponse> listResumes(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    return tailoringService.list(principal.getName(), page, size);
  }

  @GetMapping("/resumes/{id}")
  public ResumeDetailResponse getResume(@PathVariable Long id, Principal principal) {
    return tailoringService.get(principal.getName(), id);
  }

  @GetMapping("/resumes/{id}/pdf")
  public ResponseEntity<byte[]> getResumePdf(@PathVariable Long id, Principal principal) {
    byte[] pdf = tailoringService.getPdf(principal.getName(), id);
    return pdfResponse(pdf, "resume-" + id + ".pdf");
  }

  @PostMapping("/cover-letter")
  public ResponseEntity<CoverLetterResponse> generateCoverLetter(
      @Valid @RequestBody CoverLetterRequest request, Principal principal) {
    CoverLetterResponse created = coverLetterService.generate(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/cover-letters/{id}/pdf")
  public ResponseEntity<byte[]> getCoverLetterPdf(@PathVariable Long id, Principal principal) {
    byte[] pdf = coverLetterService.getPdf(principal.getName(), id);
    return pdfResponse(pdf, "cover-letter-" + id + ".pdf");
  }

  @GetMapping("/templates")
  public List<TemplateResponse> listTemplates() {
    return Stream.concat(
            templateRegistry.list(TemplateRegistry.Kind.RESUME).stream(),
            templateRegistry.list(TemplateRegistry.Kind.COVER_LETTER).stream())
        .map(t -> new TemplateResponse(t.id(), t.displayName(), t.kind().name()))
        .toList();
  }

  private static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(pdf);
  }
}
