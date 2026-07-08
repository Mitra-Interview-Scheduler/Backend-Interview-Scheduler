package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_domains",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "domain_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;
}
