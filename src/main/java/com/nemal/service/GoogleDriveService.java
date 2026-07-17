package com.nemal.service;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.nemal.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);

    private final GoogleCalendarTokenService tokenService;

    public GoogleDriveService(GoogleCalendarTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /** Result of a successful Drive upload — the pieces needed to build a Calendar attachment. */
    public record UploadedFile(String fileId, String webViewLink, String mimeType, String title) {}

    /**
     * Uploads a candidate document to the organizer's Google Drive and grants
     * anyone-with-the-link reader access so interview attendees can open it as a
     * Google Calendar attachment.
     *
     * <p>Returns {@code null} on any problem (organizer hasn't granted the Drive
     * scope, upload/permission failure, etc.) so the caller can fall back to a
     * plain description link. Never throws.
     */
    public UploadedFile uploadInterviewAttachment(
            User organizer,
            String fileName,
            String mimeType,
            byte[] data) {
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

            Permission anyoneReader = new Permission();
            anyoneReader.setType("anyone");
            anyoneReader.setRole("reader");
            drive.permissions()
                    .create(uploaded.getId(), anyoneReader)
                    .execute();

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
}
