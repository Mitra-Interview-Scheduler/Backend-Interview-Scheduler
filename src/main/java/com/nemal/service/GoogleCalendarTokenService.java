package com.nemal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.nemal.entity.User;
import com.nemal.entity.UserGoogleCalendarCredentials;
import com.nemal.repository.UserGoogleCalendarCredentialsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class GoogleCalendarTokenService {

    /** Full calendar access: list subscribed calendars and read/write events on all of them. */
    public static final List<String> CALENDAR_SCOPES = List.of(CalendarScopes.CALENDAR);

    private final UserGoogleCalendarCredentialsRepository credentialsRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleCalendarTokenService(
            UserGoogleCalendarCredentialsRepository credentialsRepository,
            TokenEncryptionService tokenEncryptionService,
            ObjectMapper objectMapper,
            @Value("${google.client.id}") String clientId,
            @Value("${google.client.secret:}") String clientSecret,
            @Value("${google.calendar.redirect-uri:}") String redirectUri) {
        this.credentialsRepository = credentialsRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public boolean isConnected(User user) {
        return credentialsRepository.findByUserId(user.getId()).isPresent();
    }

    public Optional<UserGoogleCalendarCredentials> findCredentials(User user) {
        return credentialsRepository.findByUserId(user.getId());
    }

    public Optional<UserGoogleCalendarCredentials> findCredentials(Long userId) {
        return credentialsRepository.findByUserId(userId);
    }

    @Transactional
    public UserGoogleCalendarCredentials saveTokens(
            User user,
            String googleAccountEmail,
            String accessToken,
            String refreshToken,
            long expiresInSeconds) {
        UserGoogleCalendarCredentials credentials = credentialsRepository.findByUserId(user.getId())
                .orElse(UserGoogleCalendarCredentials.builder().user(user).build());

        credentials.setGoogleAccountEmail(googleAccountEmail);
        credentials.setEncryptedAccessToken(tokenEncryptionService.encrypt(accessToken));
        if (refreshToken != null && !refreshToken.isBlank()) {
            credentials.setEncryptedRefreshToken(tokenEncryptionService.encrypt(refreshToken));
        }
        credentials.setTokenExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(expiresInSeconds));
        credentials.setScopes(String.join(",", CALENDAR_SCOPES));
        return credentialsRepository.save(credentials);
    }

    @Transactional
    public void deleteCredentials(User user) {
        credentialsRepository.deleteByUserId(user.getId());
    }

    /**
     * @return empty optional when the user has never saved a Mitra calendar selection
     *         (legacy: follow Google's selected calendars).
     */
    public Optional<List<String>> findSelectedCalendarIds(User user) {
        return findCredentials(user)
                .map(UserGoogleCalendarCredentials::getSelectedCalendarIds)
                .filter(json -> json != null && !json.isBlank())
                .map(this::parseCalendarIds);
    }

    public boolean hasCustomCalendarSelection(User user) {
        return findCredentials(user)
                .map(UserGoogleCalendarCredentials::getSelectedCalendarIds)
                .filter(json -> json != null && !json.isBlank())
                .isPresent();
    }

    @Transactional
    public List<String> saveSelectedCalendarIds(User user, List<String> calendarIds) {
        UserGoogleCalendarCredentials credentials = credentialsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Google Calendar is not connected"));

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (calendarIds != null) {
            for (String id : calendarIds) {
                if (id != null && !id.isBlank()) {
                    unique.add(id.trim());
                }
            }
        }
        try {
            // Empty list is intentional: show no Google calendars on My Availability.
            credentials.setSelectedCalendarIds(objectMapper.writeValueAsString(new ArrayList<>(unique)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist calendar selection", e);
        }
        credentialsRepository.save(credentials);
        return new ArrayList<>(unique);
    }

    private List<String> parseCalendarIds(String json) {
        try {
            List<String> ids = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Calendar buildCalendarClient(User user) throws IOException, GeneralSecurityException {
        UserGoogleCalendarCredentials credentials = credentialsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Google Calendar is not connected for user " + user.getId()));

        Credential credential = buildCredential(credentials);
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Calendar.Builder(transport, GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Mitra Interview Scheduler")
                .build();
    }

    public GoogleAuthorizationCodeFlow buildAuthorizationFlow() throws IOException, GeneralSecurityException {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        clientSecrets.setInstalled(details);

        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                CALENDAR_SCOPES)
                .setDataStoreFactory(new MemoryDataStoreFactory())
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public GoogleTokenResponse exchangeCode(String code) throws IOException, GeneralSecurityException {
        return buildAuthorizationFlow()
                .newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();
    }

    public void revokeToken(User user) {
        // Best-effort: credentials are deleted on disconnect; Google tokens expire naturally.
        findCredentials(user);
    }

    private Credential buildCredential(UserGoogleCalendarCredentials credentials)
            throws IOException, GeneralSecurityException {
        GoogleAuthorizationCodeFlow flow = buildAuthorizationFlow();
        String accessToken = tokenEncryptionService.decrypt(credentials.getEncryptedAccessToken());
        String refreshToken = tokenEncryptionService.decrypt(credentials.getEncryptedRefreshToken());

        Credential credential = new Credential.Builder(flow.getMethod())
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientAuthentication(flow.getClientAuthentication())
                .setTokenServerUrl(new GenericUrl(flow.getTokenServerEncodedUrl()))
                .build()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken);

        if (credentials.getTokenExpiresAt() != null) {
            long expiresAtMillis = credentials.getTokenExpiresAt()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
            credential.setExpirationTimeMilliseconds(expiresAtMillis);
        }

        if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
            credential.refreshToken();
            credentials.setEncryptedAccessToken(tokenEncryptionService.encrypt(credential.getAccessToken()));
            if (credential.getRefreshToken() != null) {
                credentials.setEncryptedRefreshToken(tokenEncryptionService.encrypt(credential.getRefreshToken()));
            }
            long expiresIn = credential.getExpiresInSeconds() != null ? credential.getExpiresInSeconds() : 3600;
            credentials.setTokenExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(expiresIn));
            credentialsRepository.save(credentials);
        }

        return credential;
    }
}
