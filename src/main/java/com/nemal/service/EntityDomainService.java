package com.nemal.service;

import com.nemal.dto.DomainDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateDomain;
import com.nemal.entity.Domain;
import com.nemal.entity.User;
import com.nemal.entity.UserDomain;
import com.nemal.repository.CandidateDomainRepository;
import com.nemal.repository.UserDomainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EntityDomainService {

    private final DomainService domainService;
    private final UserDomainRepository userDomainRepository;
    private final CandidateDomainRepository candidateDomainRepository;

    public EntityDomainService(
            DomainService domainService,
            UserDomainRepository userDomainRepository,
            CandidateDomainRepository candidateDomainRepository
    ) {
        this.domainService = domainService;
        this.userDomainRepository = userDomainRepository;
        this.candidateDomainRepository = candidateDomainRepository;
    }

    @Transactional(readOnly = true)
    public List<DomainDto> getUserDomains(Long userId) {
        return userDomainRepository.findByUserId(userId).stream()
                .map(UserDomain::getDomain)
                .filter(Objects::nonNull)
                .filter(Domain::isActive)
                .map(DomainDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<DomainDto>> getDomainsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userDomainRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        ud -> ud.getUser().getId(),
                        Collectors.mapping(
                                ud -> ud.getDomain() != null ? DomainDto.from(ud.getDomain()) : null,
                                Collectors.filtering(Objects::nonNull, Collectors.toList())
                        )
                ));
    }

    @Transactional(readOnly = true)
    public List<DomainDto> getCandidateDomains(Long candidateId) {
        return candidateDomainRepository.findByCandidateId(candidateId).stream()
                .map(CandidateDomain::getDomain)
                .filter(Objects::nonNull)
                .filter(Domain::isActive)
                .map(DomainDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<DomainDto>> getDomainsByCandidateIds(Collection<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return candidateDomainRepository.findByCandidateIdIn(candidateIds).stream()
                .collect(Collectors.groupingBy(
                        cd -> cd.getCandidate().getId(),
                        Collectors.mapping(
                                cd -> cd.getDomain() != null ? DomainDto.from(cd.getDomain()) : null,
                                Collectors.filtering(Objects::nonNull, Collectors.toList())
                        )
                ));
    }

    @Transactional
    public void syncUserDomains(User user, List<Long> domainIds) {
        if (domainIds == null) {
            return;
        }

        Set<Long> desired = distinctIds(domainIds);
        List<UserDomain> existing = userDomainRepository.findByUserId(user.getId());
        Set<Long> current = existing.stream()
                .map(EntityDomainService::extractUserDomainId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        for (UserDomain link : existing) {
            Long domainId = extractUserDomainId(link);
            if (domainId == null || !desired.contains(domainId)) {
                userDomainRepository.delete(link);
            }
        }

        for (Long domainId : desired) {
            if (!current.contains(domainId)) {
                userDomainRepository.save(UserDomain.builder()
                        .user(user)
                        .domain(domainService.requireActiveDomain(domainId))
                        .build());
            }
        }
    }

    @Transactional
    public void syncCandidateDomains(Candidate candidate, List<Long> domainIds) {
        if (domainIds == null) {
            return;
        }

        Set<Long> desired = distinctIds(domainIds);
        List<CandidateDomain> existing = candidateDomainRepository.findByCandidateId(candidate.getId());
        Set<Long> current = existing.stream()
                .map(EntityDomainService::extractCandidateDomainId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        for (CandidateDomain link : existing) {
            Long domainId = extractCandidateDomainId(link);
            if (domainId == null || !desired.contains(domainId)) {
                candidateDomainRepository.delete(link);
            }
        }

        for (Long domainId : desired) {
            if (!current.contains(domainId)) {
                candidateDomainRepository.save(CandidateDomain.builder()
                        .candidate(candidate)
                        .domain(domainService.requireActiveDomain(domainId))
                        .build());
            }
        }
    }

    private static Long extractUserDomainId(UserDomain link) {
        return link != null && link.getDomain() != null ? link.getDomain().getId() : null;
    }

    private static Long extractCandidateDomainId(CandidateDomain link) {
        return link != null && link.getDomain() != null ? link.getDomain().getId() : null;
    }

    private static Set<Long> distinctIds(List<Long> domainIds) {
        return domainIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
