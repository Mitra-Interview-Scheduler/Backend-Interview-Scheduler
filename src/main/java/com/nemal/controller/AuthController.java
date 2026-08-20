package com.nemal.controller;

import com.nemal.dto.LoginDto;
import com.nemal.dto.LoginResponse;
import com.nemal.dto.UserRegistrationDto;
import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import com.nemal.service.GoogleAuthService;
import com.nemal.service.RefreshTokenService;
import com.nemal.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final GoogleAuthService googleAuthService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    public AuthController(
            UserService userService,
            GoogleAuthService googleAuthService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository
    ) {
        this.userService = userService;
        this.googleAuthService = googleAuthService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        );
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(
            @Valid @RequestBody UserRegistrationDto dto,
            HttpServletResponse response
    ) {
        LoginResponse body = userService.register(dto);
        issueRefreshCookie(body.id(), response);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone,
            HttpServletResponse response
    ) {
        LoginResponse body = userService.authenticate(dto, timezone);
        issueRefreshCookie(body.id(), response);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @RequestBody Map<String, String> data,
            @RequestHeader(value = "X-Timezone", required = false) String timezone,
            HttpServletResponse response
    ) {
        String googleToken = data.get("token");
        LoginResponse body = googleAuthService.authenticateGoogleUser(googleToken, timezone);
        issueRefreshCookie(body.id(), response);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(refreshTokenService.refresh(request, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()
        ));
    }

    private void issueRefreshCookie(Long userId, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        refreshTokenService.issueNewSession(user, response);
    }
}
