package com.nemal.repository;

import com.nemal.entity.UserDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserDomainRepository extends JpaRepository<UserDomain, Long> {
    List<UserDomain> findByUserId(Long userId);

    List<UserDomain> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserIdAndDomainId(Long userId, Long domainId);

    @Query("""
            SELECT DISTINCT ud.user.id FROM UserDomain ud
            WHERE ud.domain.id IN :domainIds
            AND ud.domain.isActive = true
            """)
    List<Long> findUserIdsByDomainIds(@Param("domainIds") Collection<Long> domainIds);
}
