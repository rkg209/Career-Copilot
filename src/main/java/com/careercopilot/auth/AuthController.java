package com.careercopilot.auth;

import com.careercopilot.auth.dto.AuthResponseDto;
import com.careercopilot.auth.dto.LoginRequestDto;
import com.careercopilot.auth.dto.RegisterRequestDto;
import com.careercopilot.auth.dto.UserResponseDto;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto dto) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(dto));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
    return ResponseEntity.ok(authService.login(dto));
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponseDto> me(Principal principal) {
    return ResponseEntity.ok(authService.getByEmail(principal.getName()));
  }
}
