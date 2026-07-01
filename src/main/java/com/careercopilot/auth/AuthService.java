package com.careercopilot.auth;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.auth.dto.UserResponseDto;
import com.careercopilot.shared.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public UserResponseDto register(RegisterRequestDto dto) {
    if (userRepository.existsByEmail(dto.email())) {
      throw new EmailAlreadyRegisteredException(dto.email());
    }
    User user = new User(dto.email(), passwordEncoder.encode(dto.password()));
    User saved = userRepository.save(user);
    return new UserResponseDto(saved.getId(), saved.getEmail());
  }

  public AuthResponseDto login(LoginRequestDto dto) {
    User user =
        userRepository.findByEmail(dto.email()).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    String token = jwtService.generateToken(user.getId(), user.getEmail());
    return new AuthResponseDto(token, jwtService.getExpirySeconds());
  }

  public UserResponseDto getByEmail(String email) {
    User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    return new UserResponseDto(user.getId(), user.getEmail());
  }
}
