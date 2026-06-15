package com.nemal.entity;

import com.nemal.enums.AuthProvider;
import com.nemal.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// ─── FIX ────────────────────────────────────────────────────────────────────
// @Data generates hashCode() from ALL fields including the lazy Set
// `interviewerTechnologies`. When Hibernate loads InterviewPanel.panelRequests
// (a Set<InterviewRequest>), it calls InterviewRequest.hashCode() →
// User.hashCode() → tries to load `interviewerTechnologies` while Hibernate is
// still building that outer Set → ConcurrentModificationException.
//
// Solution: only use `id` for equals/hashCode. Safe, stable, correct for JPA.
// ────────────────────────────────────────────────────────────────────────────
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"interviewerTechnologies", "currentDesignation", "department", "settings"})
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column()
    private String passwordHash;

    private String firstName;
    private String lastName;
    private String phone;
    private String profilePictureUrl;

    @Column(length = 1000)
    private String bio;

    private Integer yearsOfExperience;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider;

    @ManyToOne
    @JoinColumn(name = "current_designation_id")
    private Designation currentDesignation;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserSettings settings;

    // NO @Where CLAUSE - Let the code filter active technologies manually
    @OneToMany(mappedBy = "interviewer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<InterviewerTechnology> interviewerTechnologies = new HashSet<>();

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isActive; }

    public void setIsActive(boolean b) { this.isActive = b; }

    public Set<InterviewerTechnology> getInterviewerTechnologies() {
        if (interviewerTechnologies == null) {
            interviewerTechnologies = new HashSet<>();
        }
        return interviewerTechnologies;
    }

    public boolean isActive() { return isActive; }

    public void setActive(boolean active) { isActive = active; }
}