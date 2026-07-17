package com.nemal.service;

import com.google.api.services.calendar.model.EventAttachment;
import com.nemal.entity.CandidateDocument;
import com.nemal.entity.User;
import com.nemal.repository.CandidateDocumentRepository;
import com.nemal.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Uploads candidate documents to Drive and attaches them to an already-created
 * Google Calendar / Meet event. Runs off the booking request path so scheduling
 * stays fast.
 */
@Service
public class CalendarAttachmentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarAttachmentSyncService.class);

    private final UserRepository userRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final GoogleDriveService driveService;
    private final GoogleCalendarEventService eventService;
    private final String frontendUrl;

    public CalendarAttachmentSyncService(
            UserRepository userRepository,
            CandidateDocumentRepository candidateDocumentRepository,
            GoogleDriveService driveService,
            GoogleCalendarEventService eventService,
            @Value("${app.frontend.url:}") String frontendUrl) {
        this.userRepository = userRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.driveService = driveService;
        this.eventService = eventService;
        this.frontendUrl = frontendUrl != null && frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : (frontendUrl != null ? frontendUrl : "");
    }

    @Async
    @Transactional(readOnly = true)
    public void syncCandidateDocumentsToEvent(Long organizerUserId, Long candidateId, String eventId) {
        if (organizerUserId == null || candidateId == null || eventId == null || eventId.isBlank()) {
            return;
        }

        try {
            User organizer = userRepository.findById(organizerUserId).orElse(null);
            if (organizer == null) {
                logger.warn("Skipping attachment sync for event {}: organizer {} not found", eventId, organizerUserId);
                return;
            }

            List<GoogleCalendarEventService.ResourceLink> links = new ArrayList<>();
            List<EventAttachment> attachments = new ArrayList<>();
            boolean anyFallback = false;

            List<CandidateDocument> docs = candidateDocumentRepository
                    .findByCandidateIdOrderByCreatedAtDesc(candidateId);
            for (CandidateDocument doc : docs) {
                if (doc == null || doc.getFileData() == null || doc.getFileData().length == 0) {
                    continue;
                }
                String title = buildDocumentAttachmentTitle(doc);
                GoogleDriveService.UploadedFile uploaded = driveService.uploadInterviewAttachment(
                        organizer, doc.getFileName(), doc.getContentType(), doc.getFileData());
                if (uploaded != null) {
                    attachments.add(eventService.buildDriveAttachment(
                            uploaded.fileId(),
                            uploaded.webViewLink(),
                            uploaded.mimeType(),
                            title));
                    links.add(new GoogleCalendarEventService.ResourceLink(title, uploaded.webViewLink()));
                } else {
                    anyFallback = true;
                }
            }

            if (anyFallback && !frontendUrl.isBlank()) {
                links.add(new GoogleCalendarEventService.ResourceLink(
                        "Candidate documents (open in Mitra)",
                        frontendUrl + "/hr/candidates/" + candidateId + "/details"));
            }

            if (attachments.isEmpty() && links.isEmpty()) {
                logger.info("No candidate documents to attach for event {} (candidate {})", eventId, candidateId);
                return;
            }

            eventService.enrichEventWithAttachments(organizer, eventId, links, attachments);
            logger.info(
                    "Background-synced {} Drive attachment(s) and {} link(s) onto calendar event {} (candidate {})",
                    attachments.size(),
                    links.size(),
                    eventId,
                    candidateId);
        } catch (Exception e) {
            logger.warn(
                    "Background Drive attachment sync failed for event {} (candidate {}): {}",
                    eventId,
                    candidateId,
                    e.getMessage());
        }
    }

    private String buildDocumentAttachmentTitle(CandidateDocument doc) {
        String type = doc.getDocumentType() != null && !doc.getDocumentType().isBlank()
                ? doc.getDocumentType().trim()
                : "Document";
        String name = doc.getFileName() != null && !doc.getFileName().isBlank()
                ? doc.getFileName().trim()
                : "attachment";
        return type + " - " + name;
    }
}
