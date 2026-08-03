package com.careercopilot.trainer;

import com.careercopilot.auth.User;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.shared.BadRequestException;
import com.careercopilot.shared.PageResponse;
import com.careercopilot.shared.ResourceNotFoundException;
import com.careercopilot.trainer.dto.MockQuestionResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of {@code GET /api/v1/trainer/questions}: paginated, joined to the latest grade. */
@Service
@Transactional(readOnly = true)
public class MockQuestionQueryService {

  private static final int MAX_PAGE_SIZE = 100;

  private final UserRepository userRepository;
  private final MockQuestionRepository mockQuestionRepository;
  private final PracticeAnswerRepository practiceAnswerRepository;

  public MockQuestionQueryService(
      UserRepository userRepository,
      MockQuestionRepository mockQuestionRepository,
      PracticeAnswerRepository practiceAnswerRepository) {
    this.userRepository = userRepository;
    this.mockQuestionRepository = mockQuestionRepository;
    this.practiceAnswerRepository = practiceAnswerRepository;
  }

  public PageResponse<MockQuestionResponse> list(String email, int page, int size) {
    User user = resolveUser(email);
    Pageable pageable =
        PageRequest.of(page, validatedSize(size), Sort.by("createdAt").descending());
    Page<MockQuestion> result = mockQuestionRepository.findByUserId(user.getId(), pageable);
    List<MockQuestionResponse> content =
        result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(result, content);
  }

  private MockQuestionResponse toResponse(MockQuestion question) {
    Integer lastGrade =
        practiceAnswerRepository
            .findTop1ByMockQuestionIdAndUserIdOrderByAnsweredAtDesc(
                question.getId(), question.getUserId())
            .stream()
            .findFirst()
            .map(PracticeAnswer::getQualityGrade)
            .orElse(null);
    return new MockQuestionResponse(
        question.getId(),
        question.getCompany(),
        question.getRoleCategory(),
        question.getRoundType(),
        question.getQuestionText(),
        question.getSource(),
        question.getSourceRefId(),
        lastGrade,
        question.getCreatedAt());
  }

  private static int validatedSize(int size) {
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    return size;
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
  }
}
