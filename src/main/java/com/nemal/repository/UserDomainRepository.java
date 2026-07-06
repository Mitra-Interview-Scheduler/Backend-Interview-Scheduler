package com.nemal.repository;

import com.nemal.entity.UserDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserDomainRepository extends JpaRepository<UserDomain, Long> {
    List<UserDomain> findByUserId(Long userId);

    List<UserDomain> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserIdAndDomainId(Long userId, Long domainId);
}
