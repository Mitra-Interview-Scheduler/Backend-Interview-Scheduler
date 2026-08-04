package com.nemal.service;

import com.nemal.entity.Candidate;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.User;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.RequestStatus;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.InterviewRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a candidate's Recruitment Shared Drive folder shared with exactly the right people.
 *
 * <p>The app is authoritative for <i>who should have access</i>; this reconciler projects that
 * onto the folder's Drive ACL. It computes the desired reader set, reads the live set from Drive,
 * and applies the diff — granting the missing and revoking the rest (which also cleans up any
 * unauthorized manual shares). Admins are expected to be members of the Shared Drive itself, so
 * they are not granted per-folder.
 *
 * <p>Runs async and never throws, so scheduling/candidate flows are never blocked by Drive latency.
 */
@Service
public class CandidateFolderAccessService {

    private static final Logger logger = LoggerFactory.getLogger(CandidateFolderAccessService.class);

    /** Once a candidate reaches one of these, transient interviewer/coordinator access is revoked. */
    private static final Set<MasterStatus> CLOSED_STATUSES = EnumSet.of(
            MasterStatus.SELECTED, MasterStatus.REJECTED, MasterStatus.WITHDRAWN);

    private static final Set<RequestStatus> INACTIVE_REQUEST = EnumSet.of(
            RequestStatus.CANCELLED, RequestStatus.REJECTED);

    private final CandidateRepository candidateRepository;
    private final InterviewRequestRepository interviewRequestRepository;
    private final RecruitmentDriveService driveService;
    private final String workspaceDomain;
    private final boolean enforceDomain;

    public CandidateFolderAccessService(
            CandidateRepository candidateRepository,
            InterviewRequestRepository interviewRequestRepository,
            RecruitmentDriveService driveService,
            @Value("${google.workspace.domain:}") String workspaceDomain,
            @Value("${google.recruitment.enforce-domain:false}") boolean enforceDomain) {
        this.candidateRepository = candidateRepository;
        this.interviewRequestRepository = interviewRequestRepository;
        this.driveService = driveService;
        this.workspaceDomain = workspaceDomain == null ? "" : workspaceDomain.trim().toLowerCase(Locale.ROOT);
        this.enforceDomain = enforceDomain;
    }

    @Async
    @Transactional(readOnly = true)
    public void reconcile(Long candidateId) {
        if (candidateId == null || !driveService.isConfigured()) {
            return;
        }
        Candidate candidate = candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            return;
        }
        String folderId = candidate.getDriveFolderId();
        if (folderId == null || folderId.isBlank()) {
            return;
        }

        try {
            Set<String> desired = computeDesiredReaders(candidate);
            Map<String, String> current = driveService.listReaderEmails(folderId); // email(lower) -> permissionId

            // Grant anyone desired who isn't already a reader.
            for (String email : desired) {
                if (!current.containsKey(email)) {
                    driveService.grantReader(folderId, email);
                }
            }
            // Revoke any reader who is no longer desired (incl. manual shares).
            for (Map.Entry<String, String> entry : current.entrySet()) {
                if (!desired.contains(entry.getKey())) {
                    driveService.revoke(folderId, entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to reconcile Drive access for candidate {}: {}", candidateId, e.getMessage());
        }
    }

    /** createdBy + coordinatedHr + (active interviewers/coordinators, unless the candidate is closed). */
    private Set<String> computeDesiredReaders(Candidate candidate) {
        Set<String> desired = new LinkedHashSet<>();
        addEmail(desired, candidate.getCreatedBy());
        addEmail(desired, candidate.getCoordinatedHr());

        boolean closed = candidate.getStatus() != null && CLOSED_STATUSES.contains(candidate.getStatus());
        if (!closed) {
            List<InterviewRequest> requests = interviewRequestRepository.findByCandidateId(candidate.getId());
            for (InterviewRequest r : requests) {
                if (r.getStatus() != null && INACTIVE_REQUEST.contains(r.getStatus())) {
                    continue;
                }
                addEmail(desired, r.getAssignedInterviewer());
                addEmail(desired, r.getInterviewCoordinator());
            }
        }
        return desired;
    }

    private void addEmail(Set<String> set, User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        if (enforceDomain && !workspaceDomain.isBlank() && !email.endsWith("@" + workspaceDomain)) {
            return; // live policy: only grant workspace-domain users
        }
        set.add(email);
    }
}
