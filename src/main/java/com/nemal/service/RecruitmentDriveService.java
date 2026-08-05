package com.nemal.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.model.PermissionList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backend Drive access for the org-owned "Mitra Recruitment" Shared Drive, using a
 * <b>service-account</b> identity (not a per-user OAuth token). Files created in the Shared
 * Drive are owned by the organisation, so they survive any employee leaving.
 *
 * <p>Two membership modes are supported:
 * <ul>
 *   <li>Direct: the service account is a Manager on the Shared Drive.</li>
 *   <li>Domain-wide delegation: when Workspace only allows {@code @company.com} members,
 *       set {@code google.recruitment.impersonate-user} to a Manager on the Shared Drive.
 *       The SA then acts as that user (Admin Console must authorize Drive scope for the SA).</li>
 * </ul>
 *
 * <p>Provides the low-level primitives — create folder, upload file, list/grant/revoke
 * folder readers. All calls set {@code supportsAllDrives(true)} as required for Shared
 * Drives. Every method degrades gracefully (logs + returns null/empty) when not configured.
 */
@Service
public class RecruitmentDriveService {

    private static final Logger logger = LoggerFactory.getLogger(RecruitmentDriveService.class);
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String APP_NAME = "Mitra Recruitment";

    private final String serviceAccountKeyPath;
    private final String serviceAccountKeyBase64;
    private final String sharedDriveId;
    private final String impersonateUser;

    private volatile Drive cachedClient;

    public RecruitmentDriveService(
            @Value("${google.recruitment.service-account-key-path:}") String serviceAccountKeyPath,
            @Value("${google.recruitment.service-account-key-base64:}") String serviceAccountKeyBase64,
            @Value("${google.recruitment.shared-drive-id:}") String sharedDriveId,
            @Value("${google.recruitment.impersonate-user:}") String impersonateUser) {
        this.serviceAccountKeyPath = serviceAccountKeyPath;
        this.serviceAccountKeyBase64 = serviceAccountKeyBase64;
        this.sharedDriveId = sharedDriveId;
        this.impersonateUser = normalizeEmail(impersonateUser);
    }

    /** Whether the Recruitment Drive integration is configured and usable. */
    public boolean isConfigured() {
        return (hasText(serviceAccountKeyPath) || hasText(serviceAccountKeyBase64)) && hasText(sharedDriveId);
    }

    // ── Folders & files ───────────────────────────────────────────────────────

    /** Creates a folder inside the Recruitment Shared Drive; returns its id, or null on failure. */
    public String createFolder(String name) {
        if (!isConfigured()) {
            return null;
        }
        try {
            Drive drive = client();
            File metadata = new File();
            metadata.setName(name);
            metadata.setMimeType(FOLDER_MIME);
            metadata.setParents(List.of(sharedDriveId));
            metadata.setDriveId(sharedDriveId);
            File created = drive.files().create(metadata)
                    .setSupportsAllDrives(true)
                    .setFields("id")
                    .execute();
            return created.getId();
        } catch (Exception e) {
            logger.warn("Failed to create Recruitment Drive folder '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /** Uploads a file into the given folder; returns {fileId, webViewLink} or null on failure. */
    public UploadedFile uploadFile(String folderId, String fileName, String mimeType, byte[] data) {
        if (!isConfigured() || folderId == null || data == null) {
            return null;
        }
        try {
            Drive drive = client();
            File metadata = new File();
            metadata.setName(fileName != null ? fileName : "document");
            metadata.setParents(List.of(folderId));
            File created = drive.files()
                    .create(metadata, new ByteArrayContent(
                            mimeType != null ? mimeType : "application/octet-stream", data))
                    .setSupportsAllDrives(true)
                    .setFields("id, webViewLink")
                    .execute();
            return new UploadedFile(created.getId(), created.getWebViewLink());
        } catch (Exception e) {
            logger.warn("Failed to upload '{}' to Recruitment Drive folder {}: {}",
                    fileName, folderId, e.getMessage());
            return null;
        }
    }

    /** Best-effort delete of a Drive file/folder (used when a candidate/document is removed). */
    public void deleteFile(String fileId) {
        if (!isConfigured() || !hasText(fileId)) {
            return;
        }
        try {
            client().files().delete(fileId).setSupportsAllDrives(true).execute();
        } catch (Exception e) {
            logger.warn("Failed to delete Recruitment Drive file {}: {}", fileId, e.getMessage());
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Lists the current reader grants on a folder as email → permissionId. The owner/
     * organiser permissions (type != user, or no email) are excluded.
     */
    public Map<String, String> listReaderEmails(String folderId) {
        Map<String, String> byEmail = new LinkedHashMap<>();
        if (!isConfigured() || !hasText(folderId)) {
            return byEmail;
        }
        try {
            Drive drive = client();
            String pageToken = null;
            do {
                PermissionList result = drive.permissions().list(folderId)
                        .setSupportsAllDrives(true)
                        .setFields("nextPageToken, permissions(id, type, role, emailAddress)")
                        .setPageToken(pageToken)
                        .execute();
                List<Permission> perms = result.getPermissions();
                if (perms != null) {
                    for (Permission p : perms) {
                        if ("user".equals(p.getType()) && p.getEmailAddress() != null) {
                            byEmail.put(p.getEmailAddress().toLowerCase(), p.getId());
                        }
                    }
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        } catch (Exception e) {
            logger.warn("Failed to list permissions on Recruitment Drive folder {}: {}",
                    folderId, e.getMessage());
        }
        return byEmail;
    }

    /** Grants a user reader access to a folder (cascades to files inside). */
    public void grantReader(String folderId, String email) {
        if (!isConfigured() || !hasText(folderId) || !hasText(email)) {
            return;
        }
        try {
            Permission permission = new Permission();
            permission.setType("user");
            permission.setRole("reader");
            permission.setEmailAddress(email);
            client().permissions().create(folderId, permission)
                    .setSupportsAllDrives(true)
                    .setSendNotificationEmail(false)
                    .execute();
        } catch (Exception e) {
            logger.warn("Failed to grant reader {} on Recruitment Drive folder {}: {}",
                    email, folderId, e.getMessage());
        }
    }

    /** Removes a specific permission (by id) from a folder. */
    public void revoke(String folderId, String permissionId) {
        if (!isConfigured() || !hasText(folderId) || !hasText(permissionId)) {
            return;
        }
        try {
            client().permissions().delete(folderId, permissionId)
                    .setSupportsAllDrives(true)
                    .execute();
        } catch (Exception e) {
            logger.warn("Failed to revoke permission {} on Recruitment Drive folder {}: {}",
                    permissionId, folderId, e.getMessage());
        }
    }

    public record UploadedFile(String fileId, String webViewLink) {}

    // ── Client ────────────────────────────────────────────────────────────────

    private Drive client() throws Exception {
        Drive local = cachedClient;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedClient == null) {
                GoogleCredentials credentials = buildCredentials();
                cachedClient = new Drive.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APP_NAME)
                        .build();
                if (hasText(impersonateUser)) {
                    logger.info("Recruitment Drive client using domain-wide delegation as {}", impersonateUser);
                }
            }
            return cachedClient;
        }
    }

    private GoogleCredentials buildCredentials() throws Exception {
        GoogleCredentials credentials = loadCredentials().createScoped(List.of(DriveScopes.DRIVE));
        if (!hasText(impersonateUser)) {
            return credentials;
        }
        if (!(credentials instanceof ServiceAccountCredentials serviceAccountCredentials)) {
            throw new IllegalStateException(
                    "google.recruitment.impersonate-user requires a service-account JSON key");
        }
        return serviceAccountCredentials.createDelegated(impersonateUser);
    }

    private GoogleCredentials loadCredentials() throws Exception {
        if (hasText(serviceAccountKeyBase64)) {
            byte[] json = Base64.getDecoder().decode(serviceAccountKeyBase64.trim());
            try (InputStream in = new ByteArrayInputStream(json)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        try (InputStream in = new FileInputStream(serviceAccountKeyPath)) {
            return GoogleCredentials.fromStream(in);
        }
    }

    private static String normalizeEmail(String email) {
        if (!hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
