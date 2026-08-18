package com.nemal.controller;

import com.nemal.dto.LoginDto;
import com.nemal.dto.LoginResponse;
import com.nemal.dto.UserRegistrationDto;
import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import com.nemal.security.AuthCookieService;
import com.nemal.security.JwtService;
import com.nemal.security.RefreshTokenService;
import com.nemal.service.GoogleAuthService;
import com.nemal.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final UserService userService;
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;

    public AuthController(
            UserService userService,
            GoogleAuthService googleAuthService,
            UserRepository userRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthCookieService authCookieService
    ) {
        this.userService = userService;
        this.googleAuthService = googleAuthService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(
            @Valid @RequestBody UserRegistrationDto dto,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        LoginResponse loginResponse = userService.register(dto);
        attachRefreshCookie(loginResponse.email(), request, response);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        LoginResponse loginResponse = userService.authenticate(dto, timezone);
        attachRefreshCookie(loginResponse.email(), request, response);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @RequestBody Map<String, String> data,
            @RequestHeader(value = "X-Timezone", required = false) String timezone,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String googleToken = data.get("token");
        LoginResponse loginResponse = googleAuthService.authenticateGoogleUser(googleToken, timezone);
        attachRefreshCookie(loginResponse.email(), request, response);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String incomingRefreshToken = authCookieService.readRefreshToken(request);
        var rotated = refreshTokenService.rotate(
                incomingRefreshToken,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );

        User user = rotated.user();
        String accessToken = jwtService.generateToken(user);
        authCookieService.writeRefreshCookie(response, rotated.newRawToken());
        return ResponseEntity.ok(LoginResponse.from(accessToken, user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String incomingRefreshToken = authCookieService.readRefreshToken(request);
        refreshTokenService.revoke(incomingRefreshToken);
        authCookieService.clearRefreshCookie(response);
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

    private void attachRefreshCookie(String email, HttpServletRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        refreshTokenService.revokeAllForUser(user.getId());
        String refreshToken = refreshTokenService.issueToken(
                user,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        authCookieService.writeRefreshCookie(response, refreshToken);
    }
}