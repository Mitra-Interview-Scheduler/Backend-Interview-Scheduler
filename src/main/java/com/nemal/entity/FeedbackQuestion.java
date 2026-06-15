package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id")
    private FeedbackForm form;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "comments_enabled", nullable = false)
    private boolean commentsEnabled;

    @Column(length = 255)
    private String placeholder;

    @Column(name = "help_text", length = 500)
    private String helpText;

    @Type(JsonBinaryType.class)
    @Column(name = "options_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode optionsJson;

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
        if (optionsJson == null) optionsJson = JsonNodeFactory.instance.arrayNode();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
