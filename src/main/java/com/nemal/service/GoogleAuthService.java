package com.nemal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nemal.dto.LoginResponse;
import com.nemal.entity.User;
import com.nemal.enums.AuthProvider;
import com.nemal.enums.Role;
import com.nemal.repository.UserRepository;
import com.nemal.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
public class GoogleAuthService {

//    private static final Set<String> TRUSTED_GOOGLE_ISSUERS = Set.of(
//            "accounts.google.com",
//            "https://accounts.google.com"
//    );

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserSettingsService userSettingsService;

    @Value("${google.client.id}")
    private String googleClientId;

    public GoogleAuthService(UserRepository userRepository, JwtService jwtService, UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userSettingsService = userSettingsService;
    }

    public LoginResponse authenticateGoogleUser(String idTokenString, String browserTimezone) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadCredentialsException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new BadCredentialsException("Invalid Google token");
            }
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new BadCredentialsException("Invalid Google token");
            }
//            if (!TRUSTED_GOOGLE_ISSUERS.contains(payload.getIssuer())) {
//                throw new BadCredentialsException("Invalid Google token");
//            }

            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> registerNewGoogleUser(payload));

            userSettingsService.ensureSettingsOnFirstLogin(user, browserTimezone);

            String jwtToken = jwtService.generateToken(user);
            return new LoginResponse(
                    jwtToken,
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRoles(),
                    user.getProfilePictureUrl()
            );
        } catch (Exception ex) {
            if (ex instanceof BadCredentialsException badCredentialsException) {
                throw badCredentialsException;
            }
            throw new BadCredentialsException("Invalid Google token");
        }
    }

    private User registerNewGoogleUser(GoogleIdToken.Payload payload) {
        String email = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String pictureUrl = (String) payload.get("picture");

        User user = User.builder()
                .email(email)
                .firstName(firstName != null ? firstName : "GoogleUser")
                .lastName(lastName != null ? lastName : "")
                .profilePictureUrl(pictureUrl)
                .roles(Set.of(Role.INTERVIEWER))
                .isActive(true)
                .authProvider(AuthProvider.GOOGLE)
                .passwordHash(null)
                .build();

        return userRepository.save(user);
    }
}


