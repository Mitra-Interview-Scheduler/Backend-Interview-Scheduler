package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback_forms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackForm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Type(JsonBinaryType.class)
    @Column(name = "department_ids_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode departmentIdsJson;

    @Type(JsonBinaryType.class)
    @Column(name = "designation_ids_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode designationIdsJson;

    @Column(name = "series_key", nullable = false, length = 36)
    private String seriesKey;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (seriesKey == null || seriesKey.isBlank()) seriesKey = UUID.randomUUID().toString();
        if (versionNumber == null) versionNumber = 1;
        if (departmentIdsJson == null) departmentIdsJson = JsonNodeFactory.instance.arrayNode();
        if (designationIdsJson == null) designationIdsJson = JsonNodeFactory.instance.arrayNode();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
