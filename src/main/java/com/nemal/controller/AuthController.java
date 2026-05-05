package com.nemal.controller;

import com.nemal.dto.LoginDto;
import com.nemal.dto.LoginResponse;
import com.nemal.dto.UserRegistrationDto;
import com.nemal.service.GoogleAuthService;
import com.nemal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final UserService userService;
    private final GoogleAuthService googleAuthService;

    public AuthController(UserService userService, GoogleAuthService googleAuthService) {
        this.userService = userService;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody UserRegistrationDto dto) {
        return ResponseEntity.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        return ResponseEntity.ok(userService.authenticate(dto, timezone));
    }
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @RequestBody Map<String, String> data,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        String googleToken = data.get("token");
        return ResponseEntity.ok(googleAuthService.authenticateGoogleUser(googleToken, timezone));
    }
    @GetMapping("/verify")
    public ResponseEntity<?> verifyToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", auth.getName(),
                "authorities", auth.getAuthorities()
        ));
    }
}