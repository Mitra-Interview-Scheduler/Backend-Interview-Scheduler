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
                        Collectors.mapping(ud -> DomainDto.from(ud.getDomain()), Collectors.toList())
                ));
    }

    @Transactional(readOnly = true)
    public List<DomainDto> getCandidateDomains(Long candidateId) {
        return candidateDomainRepository.findByCandidateId(candidateId).stream()
                .map(CandidateDomain::getDomain)
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
                        Collectors.mapping(cd -> DomainDto.from(cd.getDomain()), Collectors.toList())
                ));
    }

    @Transactional
    public void syncUserDomains(User user, List<Long> domainIds) {
        if (domainIds == null) {
            return;
        }
        applyDomainDiff(
                distinctIds(domainIds),
                userDomainRepository.findByUserId(user.getId()),
                link -> link.getDomain().getId(),
                link -> {
                    userDomainRepository.delete(link);
                    detachUserDomainFromCollection(user, link.getDomain().getId());
                },
                domainId -> {
                    if (userDomainRepository.existsByUserIdAndDomainId(user.getId(), domainId)) {
                        return;
                    }
                    userDomainRepository.save(UserDomain.builder()
                            .user(user)
                            .domain(domainService.requireActiveDomain(domainId))
                            .build());
                }
        );
    }

    @Transactional
    public void syncCandidateDomains(Candidate candidate, List<Long> domainIds) {
        if (domainIds == null) {
            return;
        }
        applyDomainDiff(
                distinctIds(domainIds),
                candidateDomainRepository.findByCandidateId(candidate.getId()),
                link -> link.getDomain().getId(),
                link -> {
                    candidateDomainRepository.delete(link);
                    detachCandidateDomainFromCollection(candidate, link.getDomain().getId());
                },
                domainId -> {
                    if (candidateDomainRepository.existsByCandidateIdAndDomainId(candidate.getId(), domainId)) {
                        return;
                    }
                    candidateDomainRepository.save(CandidateDomain.builder()
                            .candidate(candidate)
                            .domain(domainService.requireActiveDomain(domainId))
                            .build());
                }
        );
    }

    private static <T> void applyDomainDiff(
            Set<Long> desired,
            List<T> existing,
            java.util.function.Function<T, Long> domainIdExtractor,
            java.util.function.Consumer<T> removeLink,
            java.util.function.LongConsumer addDomainId
    ) {
        Set<Long> current = existing.stream()
                .map(domainIdExtractor)
                .collect(Collectors.toCollection(HashSet::new));

        for (T link : existing) {
            if (!desired.contains(domainIdExtractor.apply(link))) {
                removeLink.accept(link);
            }
        }

        for (Long domainId : desired) {
            if (!current.contains(domainId)) {
                addDomainId.accept(domainId);
            }
        }
    }

    private static Set<Long> distinctIds(List<Long> domainIds) {
        return domainIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void detachUserDomainFromCollection(User user, Long domainId) {
        Set<UserDomain> collection = user.getUserDomains();
        if (collection == null || collection.isEmpty()) {
            return;
        }
        collection.removeIf(link -> link.getDomain() != null && domainId.equals(link.getDomain().getId()));
    }

    private static void detachCandidateDomainFromCollection(Candidate candidate, Long domainId) {
        Set<CandidateDomain> collection = candidate.getCandidateDomains();
        if (collection == null || collection.isEmpty()) {
            return;
        }
        collection.removeIf(link -> link.getDomain() != null && domainId.equals(link.getDomain().getId()));
    }
}
