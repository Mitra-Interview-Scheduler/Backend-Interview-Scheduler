package com.nemal.service;

import com.nemal.entity.User;
import com.nemal.entity.UserSettings;
import com.nemal.repository.UserRepository;
import com.nemal.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTimezoneTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;

    private UserSettingsService userSettingsService;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(userRepository, userSettingsRepository);
    }

    @Test
    void resolveTimezoneForCalendarSync_usesSavedUserTimezone() {
        User user = User.builder().id(7L).email("interviewer@company.com").build();
        UserSettings settings = UserSettings.builder()
                .user(user)
                .timezone("Asia/Kolkata")
                .build();

        when(userSettingsRepository.findByUserId(7L)).thenReturn(Optional.of(settings));

        assertEquals("Asia/Kolkata", userSettingsService.resolveTimezoneForCalendarSync(user));
    }

    @Test
    void resolveTimezoneForCalendarSync_fallsBackToUtcWhenInvalid() {
        User user = User.builder().id(8L).email("interviewer@company.com").build();
        UserSettings settings = UserSettings.builder()
                .user(user)
                .timezone("Not/A_Real_Zone")
                .build();

        when(userSettingsRepository.findByUserId(8L)).thenReturn(Optional.of(settings));

        assertEquals(UserSettings.DEFAULT_TIMEZONE, userSettingsService.resolveTimezoneForCalendarSync(user));
    }

    @Test
    void resolveTimezoneForCalendarSync_fallsBackToUtcWhenBlank() {
        User user = User.builder().id(9L).email("interviewer@company.com").build();
        UserSettings settings = UserSettings.builder()
                .user(user)
                .timezone("   ")
                .build();

        when(userSettingsRepository.findByUserId(9L)).thenReturn(Optional.of(settings));

        assertEquals(UserSettings.DEFAULT_TIMEZONE, userSettingsService.resolveTimezoneForCalendarSync(user));
    }
}
