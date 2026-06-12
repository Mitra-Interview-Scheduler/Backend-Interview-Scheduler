package com.nemal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "master_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status_key", nullable = false, unique = true)
    private String statusKey;

    @Column(nullable = false)
    private String label;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "bg_color", nullable = false)
    private String bgColor;

    @Column(name = "badge_class", nullable = false)
    private String badgeClass;

    @Column(name = "light_class")
    private String lightClass;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_closing_step", nullable = false)
    @Builder.Default
    private boolean isClosingStep = false;


    @Column(name="is_default_step",nullable = false)
    @Builder.Default
    private boolean isDefaultStep = false;
}
