package com.careercopilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.auth.dto.UserResponseDto;
import com.careercopilot.profile.CandidateProfileRepository;
import com.careercopilot.shared.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private CandidateProfileRepository profileRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userRepository, profileRepository, passwordEncoder, jwtService);
  }

  @Test
  void register_savesUserAndReturnsDto() {
    RegisterRequestDto dto = new RegisterRequestDto("user@example.com", "password123");
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed");
    User saved = new User("user@example.com", "hashed");
    when(userRepository.save(any(User.class))).thenReturn(saved);

    UserResponseDto result = authService.register(dto);

    assertThat(result.email()).isEqualTo("user@example.com");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void register_duplicateEmail_throwsAndNeverSaves() {
    RegisterRequestDto dto = new RegisterRequestDto("user@example.com", "password123");
    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(dto))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void login_validCredentials_returnsToken() {
    LoginRequestDto dto = new LoginRequestDto("user@example.com", "password123");
    User user = new User("user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
    when(jwtService.generateToken(any(), any())).thenReturn("signed-jwt");
    when(jwtService.getExpirySeconds()).thenReturn(86400L);

    AuthResponseDto result = authService.login(dto);

    assertThat(result.token()).isEqualTo("signed-jwt");
    assertThat(result.expiresIn()).isEqualTo(86400L);
  }

  @Test
  void login_badPassword_throwsInvalidCredentials() {
    LoginRequestDto dto = new LoginRequestDto("user@example.com", "wrong-password");
    User user = new User("user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(dto))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_unknownEmail_throwsInvalidCredentials() {
    LoginRequestDto dto = new LoginRequestDto("nobody@example.com", "password123");
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(dto))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
