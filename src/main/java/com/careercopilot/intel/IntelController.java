package com.careercopilot.intel;

import com.careercopilot.intel.dto.CompanyIntelDetail;
import com.careercopilot.intel.dto.CompanyIntelSummary;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public (no-auth) browse of aggregated, de-identified company interview intel. */
@RestController
@RequestMapping("/api/v1/intel")
public class IntelController {

  private static final int MAX_PAGE_SIZE = 100;

  private final IntelService intelService;

  public IntelController(IntelService intelService) {
    this.intelService = intelService;
  }

  @GetMapping("/companies")
  public PageResponse<CompanyIntelSummary> browseCompanies(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    return intelService.browseCompanies(PageRequest.of(page, size));
  }

  @GetMapping("/companies/{companyName}")
  public CompanyIntelDetail byCompany(@PathVariable String companyName) {
    return intelService.byCompany(companyName);
  }
}
