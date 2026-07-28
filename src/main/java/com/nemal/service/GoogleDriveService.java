package com.nemal.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.nemal.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);

    private final GoogleCalendarTokenService tokenService;
    private final String workspaceDomain;

    public GoogleDriveService(
            GoogleCalendarTokenService tokenService,
            @Value("${google.workspace.domain:}") String workspaceDomain) {
        this.tokenService = tokenService;
        this.workspaceDomain = normalizeDomain(workspaceDomain);
    }

    /** Result of a successful Drive upload — the pieces needed to build a Calendar attachment. */
    public record UploadedFile(String fileId, String webViewLink, String mimeType, String title) {}

    /**
     * Uploads a candidate document to the organizer's Google Drive and grants
     * read access so interview attendees can open it as a Google Calendar attachment.
     *
     * <p>Prefers anyone-with-the-link when the Workspace policy allows it. If public
     * sharing is blocked ({@code publishOutNotPermitted}), falls back to domain and/or
     * per-guest reader permissions. Upload success is returned even when sharing is
     * only partial — callers can still attach the file to the calendar event.
     *
     * <p>Returns {@code null} only when the upload itself fails (no Drive scope, etc.).
     * Never throws.
     */
    public UploadedFile uploadInterviewAttachment(
            User organizer,
            String fileName,
            String mimeType,
            byte[] data) {
        return uploadInterviewAttachment(organizer, fileName, mimeType, data, List.of());
    }

    public UploadedFile uploadInterviewAttachment(
            User organizer,
            String fileName,
            String mimeType,
            byte[] data,
            Collection<String> shareWithEmails) {
        if (organizer == null || data == null || data.length == 0) {
            return null;
        }
        if (!tokenService.hasDriveAccess(organizer)) {
            logger.info("Skipping Drive upload for user {} — Drive scope not granted (needs reconnect)",
                    organizer.getId());
            return null;
        }

        String safeName = (fileName == null || fileName.isBlank()) ? "candidate-document" : fileName;
        String safeMime = (mimeType == null || mimeType.isBlank()) ? "application/octet-stream" : mimeType;

        try {
            Drive drive = tokenService.buildDriveClient(organizer);

            File metadata = new File();
            metadata.setName(safeName);

            File uploaded = drive.files()
                    .create(metadata, new ByteArrayContent(safeMime, data))
                    .setFields("id, webViewLink")
                    .execute();

            shareUploadedFile(drive, uploaded.getId(), organizer, shareWithEmails);

            String link = uploaded.getWebViewLink() != null
                    ? uploaded.getWebViewLink()
                    : "https://drive.google.com/file/d/" + uploaded.getId() + "/view";

            return new UploadedFile(uploaded.getId(), link, safeMime, safeName);
        } catch (Exception e) {
            logger.warn("Failed to upload candidate document '{}' to Drive for user {}: {}",
                    safeName, organizer.getId(), e.getMessage());
            return null;
        }
    }

    private void shareUploadedFile(
            Drive drive,
            String fileId,
            User organizer,
            Collection<String> shareWithEmails) {
        if (tryAnyoneReader(drive, fileId)) {
            return;
        }
        if (tryDomainReader(drive, fileId)) {
            // Domain share is enough for same-org guests; still grant explicit users
            // for external attendees (e.g. candidate @gmail).
            shareWithUsers(drive, fileId, organizer, shareWithEmails);
            return;
        }
        shareWithUsers(drive, fileId, organizer, shareWithEmails);
    }

    private boolean tryAnyoneReader(Drive drive, String fileId) {
        try {
            Permission anyoneReader = new Permission();
            anyoneReader.setType("anyone");
            anyoneReader.setRole("reader");
            anyoneReader.setAllowFileDiscovery(false);
            drive.permissions()
                    .create(fileId, anyoneReader)
                    .setSendNotificationEmail(false)
                    .execute();
            return true;
        } catch (GoogleJsonResponseException e) {
            String reason = extractReason(e);
            if ("publishOutNotPermitted".equals(reason) || "sharingOutNotPermitted".equals(reason)) {
                logger.info(
                        "Public Drive sharing blocked for file {} ({}), falling back to restricted sharing",
                        fileId,
                        reason);
                return false;
            }
            logger.warn("Failed to grant anyone-reader on Drive file {}: {}", fileId, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Failed to grant anyone-reader on Drive file {}: {}", fileId, e.getMessage());
            return false;
        }
    }

    private boolean tryDomainReader(Drive drive, String fileId) {
        if (workspaceDomain == null) {
            return false;
        }
        try {
            Permission domainReader = new Permission();
            domainReader.setType("domain");
            domainReader.setRole("reader");
            domainReader.setDomain(workspaceDomain);
            domainReader.setAllowFileDiscovery(false);
            drive.permissions()
                    .create(fileId, domainReader)
                    .setSendNotificationEmail(false)
                    .execute();
            return true;
        } catch (Exception e) {
            logger.info("Domain Drive sharing unavailable for file {} ({}): {}",
                    fileId, workspaceDomain, e.getMessage());
            return false;
        }
    }

    private void shareWithUsers(
            Drive drive,
            String fileId,
            User organizer,
            Collection<String> shareWithEmails) {
        Set<String> emails = new LinkedHashSet<>();
        if (shareWithEmails != null) {
            for (String email : shareWithEmails) {
                if (email != null && !email.isBlank()) {
                    emails.add(email.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (organizer != null && organizer.getEmail() != null && !organizer.getEmail().isBlank()) {
            emails.remove(organizer.getEmail().trim().toLowerCase(Locale.ROOT));
        }

        for (String email : emails) {
            try {
                Permission userReader = new Permission();
                userReader.setType("user");
                userReader.setRole("reader");
                userReader.setEmailAddress(email);
                drive.permissions()
                        .create(fileId, userReader)
                        .setSendNotificationEmail(false)
                        .execute();
            } catch (Exception e) {
                logger.warn("Failed to grant reader access on Drive file {} to {}: {}",
                        fileId, email, e.getMessage());
            }
        }
    }

    private static String extractReason(GoogleJsonResponseException e) {
        if (e.getDetails() == null || e.getDetails().getErrors() == null
                || e.getDetails().getErrors().isEmpty()) {
            return null;
        }
        return e.getDetails().getErrors().get(0).getReason();
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String trimmed = domain.trim().toLowerCase(Locale.ROOT);
        if ("yourcompany.com".equals(trimmed) || "example.com".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
