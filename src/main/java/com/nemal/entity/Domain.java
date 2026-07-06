package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "domains")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Domain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(unique = true)
    private String code;

    @Builder.Default
    private boolean isActive = true;
}
