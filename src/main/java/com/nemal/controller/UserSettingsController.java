package com.nemal.controller;

import com.nemal.dto.UpdateUserSettingsDto;
import com.nemal.dto.UserSettingsDto;
import com.nemal.entity.User;
import com.nemal.service.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile/settings")
@CrossOrigin(origins = "http://localhost:5173")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ResponseEntity<UserSettingsDto> getMySettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userSettingsService.getMySettings(user));
    }

    @PutMapping
    public ResponseEntity<UserSettingsDto> updateMySettings(
            @AuthenticationPrincipal User user,
            @RequestBody UpdateUserSettingsDto dto
    ) {
        return ResponseEntity.ok(userSettingsService.updateMySettings(user, dto));
    }
}

