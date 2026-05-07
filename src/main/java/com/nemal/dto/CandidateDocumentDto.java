package com.nemal.dto;

import com.nemal.entity.CandidateDocument;

import java.time.LocalDateTime;

public record CandidateDocumentDto(
        Long id,
        Long candidateId,
        String documentType,
        String fileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CandidateDocumentDto from(CandidateDocument document) {
        return new CandidateDocumentDto(
                document.getId(),
                document.getCandidate().getId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
