package com.nemal.service;

import com.nemal.dto.AvailabilityFilterDto;
import com.nemal.dto.InterviewerAvailabilityDto;
import com.nemal.dto.InterviewerMatchRequestDto;
import com.nemal.dto.InterviewerMatchResponseDto;
import com.nemal.dto.MatchingInterviewerDto;
import com.nemal.dto.PagedResult;
import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.CandidateTechnology;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.entity.Domain;
import com.nemal.entity.InterviewerTechnology;
import com.nemal.entity.Technology;
import com.nemal.entity.Tier;
import com.nemal.entity.User;
import com.nemal.entity.UserDomain;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.CandidateTechnologyRepository;
import com.nemal.repository.InterviewerTechnologyRepository;
import com.nemal.repository.UserDomainRepository;
import com.nemal.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.JoinType;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HRAvailabilityService {

    private static final Logger logger = LoggerFactory.getLogger(HRAvailabilityService.class);

    /**
     * How far back (in days) the HR calendar shows past slots.
     * 30 days keeps recent booked/completed interviews visible for audit.
     */
    private static final int HR_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_MATCH_LIMIT = 5;

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateTechnologyRepository candidateTechnologyRepository;
    private final InterviewerTechnologyRepository interviewerTechnologyRepository;
    private final UserDomainRepository userDomainRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public HRAvailabilityService(
            AvailabilitySlotRepository availabilitySlotRepository,
            CandidateRepository candidateRepository,
            CandidateTechnologyRepository candidateTechnologyRepository,
            InterviewerTechnologyRepository interviewerTechnologyRepository,
            UserDomainRepository userDomainRepository,
            UserRepository userRepository
    ) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.candidateRepository = candidateRepository;
        this.candidateTechnologyRepository = candidateTechnologyRepository;
        this.interviewerTechnologyRepository = interviewerTechnologyRepository;
        this.userDomainRepository = userDomainRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<InterviewerAvailabilityDto> getAllAvailableSlots(AvailabilityFilterDto filter) {
        try {
            LocalDateTime from = LocalDateTime.now().minusDays(HR_LOOKBACK_DAYS);

            logger.info("=== HR AVAILABILITY FILTER REQUEST ===");
            logger.info("Filter: {}, lookback from: {}", filter, from);

            List<AvailabilitySlot> slots = (filter == null)
                    ? availabilitySlotRepository.findAllActiveSlotsForHR(from)
                    : filterSlots(filter, from);

            logger.info("Total slots (available + booked): {}", slots.size());

            if (filter != null && shouldPrioritizeMatches(filter)) {
                slots = sortSlotsByMatchPriority(slots, filter);
            }

            List<InterviewerAvailabilityDto> result = new ArrayList<>();
            for (AvailabilitySlot slot : slots) {
                try {
                    result.add(InterviewerAvailabilityDto.from(slot));
                } catch (Exception e) {
                    logger.error("Error converting slot {} to DTO: {}", slot.getId(), e.getMessage(), e);
                }
            }
            return result;

        } catch (Exception e) {
            logger.error("Error in getAllAvailableSlots: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch available slots: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PagedResult<InterviewerAvailabilityDto> getAllAvailableSlotsPaged(AvailabilityFilterDto filter) {
        try {
            final int page = filter.page() != null ? Math.max(0, filter.page()) : 0;
            final int size = filter.size() != null ? Math.max(1, filter.size()) : 100;

            // Build dynamic criteria query
            var cb = entityManager.getCriteriaBuilder();
            var cq = cb.createQuery(AvailabilitySlot.class);
            var root = cq.from(AvailabilitySlot.class);
            root.fetch("interviewer", JoinType.LEFT);
            // Note: fetching nested joins may be omitted for performance, but keep for DTO mapping
            root.fetch("interviewSchedule", JoinType.LEFT);

            cq.select(root).distinct(true);

            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            // base: active and status in (AVAILABLE, BOOKED)
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.or(cb.equal(root.get("status"), SlotStatus.AVAILABLE), cb.equal(root.get("status"), SlotStatus.BOOKED)));

            // date range
            if (filter.startDateTime() != null && filter.endDateTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), filter.startDateTime()));
                predicates.add(cb.lessThanOrEqualTo(root.get("endDateTime"), filter.endDateTime()));
            }

            // joins for interviewer properties
                Join<AvailabilitySlot, User> interviewerJoin =
                    root.join("interviewer", JoinType.LEFT);

            // department filter
            if (filter.departmentIds() != null && !filter.departmentIds().isEmpty()) {
                Join<User, Department> deptJoin =
                    interviewerJoin.join("department", JoinType.LEFT);
                Predicate deptMatch = deptJoin.get("id").in(filter.departmentIds());
                predicates.add(deptMatch);
            }

            // technology filter: match interviewer core technologies only; booked always pass
            if (filter.technologyIds() != null && !filter.technologyIds().isEmpty()) {
                Join<User, InterviewerTechnology> itJoin =
                    interviewerJoin.join("interviewerTechnologies", JoinType.LEFT);
                Join<InterviewerTechnology, Technology> techJoin =
                    itJoin.join("technology", JoinType.LEFT);
                Predicate techActive = cb.isTrue(itJoin.get("isActive"));
                Predicate techCore = cb.isTrue(itJoin.get("isCore"));
                Predicate techMatch = techJoin.get("id").in(filter.technologyIds());
                Predicate techPredicate = cb.and(techActive, techCore, techMatch);
                predicates.add(cb.or(cb.equal(root.get("status"), SlotStatus.BOOKED), techPredicate));
            }

            // domain filter: available slots must match domains; booked always pass
            if (filter.domainIds() != null && !filter.domainIds().isEmpty()) {
                Join<User, UserDomain> udJoin =
                    interviewerJoin.join("userDomains", JoinType.LEFT);
                Join<UserDomain, Domain> domainJoin =
                    udJoin.join("domain", JoinType.LEFT);
                Predicate domainMatch = domainJoin.get("id").in(filter.domainIds());
                predicates.add(cb.or(cb.equal(root.get("status"), SlotStatus.BOOKED), domainMatch));
            }

            // min years of experience
            if (filter.minYearsOfExperience() != null) {
                Expression<Integer> years = interviewerJoin.get("yearsOfExperience").as(Integer.class);
                predicates.add(cb.or(cb.equal(root.get("status"), SlotStatus.BOOKED), cb.greaterThanOrEqualTo(years, filter.minYearsOfExperience())));
            }

            // tier/level logic (mirrors in-memory logic)
            if (filter.minTierId() != null && filter.departmentIdForDesignationFilter() != null) {
                Join<User, Designation> designationJoin =
                    interviewerJoin.join("currentDesignation", JoinType.LEFT);
                Join<Designation, Tier> tierJoin =
                    designationJoin.join("tier", JoinType.LEFT);

                // ivTier < candidateTierOrder OR (ivTier == candidateTierOrder AND ivLevel <= candidateLevelOrder)
                int candidateTierOrder = filter.minTierId().intValue();
                int candidateLevelOrder = filter.minDesignationLevelInDepartment() != null ? filter.minDesignationLevelInDepartment().intValue() : Integer.MAX_VALUE;

                Expression<Integer> ivTier = tierJoin.get("tierOrder").as(Integer.class);
                Expression<Integer> ivLevel = designationJoin.get("levelOrder").as(Integer.class);

                Predicate higherTier = cb.lessThan(ivTier, cb.literal(candidateTierOrder));
                Predicate sameTierHigherLevel = cb.and(
                    cb.equal(ivTier, cb.literal(candidateTierOrder)),
                    cb.lessThanOrEqualTo(ivLevel, cb.literal(candidateLevelOrder)));

                Predicate tierPass = cb.or(higherTier, sameTierHigherLevel);

                Join<User, Department> deptJoin =
                    interviewerJoin.join("department", JoinType.LEFT);
                Predicate deptMatch = cb.equal(deptJoin.get("id"), filter.departmentIdForDesignationFilter());

                // booked slots always pass
                predicates.add(cb.or(cb.equal(root.get("status"), SlotStatus.BOOKED), cb.and(deptMatch, tierPass)));
            }

            cq.where(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));

            var query = entityManager.createQuery(cq);
            List<AvailabilitySlot> slots;
            if (shouldPrioritizeMatches(filter)) {
                slots = query.getResultList();
                slots = sortSlotsByMatchPriority(slots, filter);
                int fromIndex = page * size;
                int toIndex = Math.min(fromIndex + size, slots.size());
                slots = fromIndex < slots.size() ? slots.subList(fromIndex, toIndex) : List.of();
            } else {
                cq.orderBy(cb.asc(root.get("startDateTime")));
                query = entityManager.createQuery(cq);
                query.setFirstResult(page * size);
                query.setMaxResults(size);
                slots = query.getResultList();
            }

            // count query
            var countCq = cb.createQuery(Long.class);
            var countRoot = countCq.from(AvailabilitySlot.class);
            // replicate joins/predicates for count
            var countPredicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            countPredicates.add(cb.isTrue(countRoot.get("isActive")));
            countPredicates.add(cb.or(cb.equal(countRoot.get("status"), SlotStatus.AVAILABLE), cb.equal(countRoot.get("status"), SlotStatus.BOOKED)));
            if (filter.startDateTime() != null && filter.endDateTime() != null) {
                countPredicates.add(cb.greaterThanOrEqualTo(countRoot.get("startDateTime"), filter.startDateTime()));
                countPredicates.add(cb.lessThanOrEqualTo(countRoot.get("endDateTime"), filter.endDateTime()));
            }
                Join<AvailabilitySlot, User> countInterviewerJoin =
                    countRoot.join("interviewer", JoinType.LEFT);
            if (filter.departmentIds() != null && !filter.departmentIds().isEmpty()) {
                    Join<User, Department> deptJoin =
                    countInterviewerJoin.join("department", JoinType.LEFT);
                countPredicates.add(deptJoin.get("id").in(filter.departmentIds()));
            }
            if (filter.technologyIds() != null && !filter.technologyIds().isEmpty()) {
                    Join<User, InterviewerTechnology> itJoin =
                    countInterviewerJoin.join("interviewerTechnologies", JoinType.LEFT);
                    Join<InterviewerTechnology, Technology> techJoin =
                    itJoin.join("technology", JoinType.LEFT);
                    Predicate techActive = cb.isTrue(itJoin.get("isActive"));
                    Predicate techCore = cb.isTrue(itJoin.get("isCore"));
                    Predicate techMatch = techJoin.get("id").in(filter.technologyIds());
                    Predicate techPredicate = cb.and(techActive, techCore, techMatch);
                countPredicates.add(cb.or(cb.equal(countRoot.get("status"), SlotStatus.BOOKED), techPredicate));
            }
            if (filter.domainIds() != null && !filter.domainIds().isEmpty()) {
                    Join<User, UserDomain> udJoin =
                    countInterviewerJoin.join("userDomains", JoinType.LEFT);
                    Join<UserDomain, Domain> domainJoin =
                    udJoin.join("domain", JoinType.LEFT);
                    Predicate domainMatch = domainJoin.get("id").in(filter.domainIds());
                countPredicates.add(cb.or(cb.equal(countRoot.get("status"), SlotStatus.BOOKED), domainMatch));
            }
            if (filter.minYearsOfExperience() != null) {
                    Expression<Integer> years = countInterviewerJoin.get("yearsOfExperience").as(Integer.class);
                    countPredicates.add(cb.or(cb.equal(countRoot.get("status"), SlotStatus.BOOKED), cb.greaterThanOrEqualTo(years, filter.minYearsOfExperience())));
            }
            if (filter.minTierId() != null && filter.departmentIdForDesignationFilter() != null) {
                    Join<User, Designation> designationJoin =
                    countInterviewerJoin.join("currentDesignation", JoinType.LEFT);
                    Join<Designation, Tier> tierJoin =
                    designationJoin.join("tier", JoinType.LEFT);
                int candidateTierOrder = filter.minTierId().intValue();
                int candidateLevelOrder = filter.minDesignationLevelInDepartment() != null ? filter.minDesignationLevelInDepartment().intValue() : Integer.MAX_VALUE;
                    Expression<Integer> ivTier = tierJoin.get("tierOrder").as(Integer.class);
                    Expression<Integer> ivLevel = designationJoin.get("levelOrder").as(Integer.class);
                    Predicate higherTier = cb.lessThan(ivTier, cb.literal(candidateTierOrder));
                    Predicate sameTierHigherLevel = cb.and(
                    cb.equal(ivTier, cb.literal(candidateTierOrder)),
                    cb.lessThanOrEqualTo(ivLevel, cb.literal(candidateLevelOrder)));
                    Predicate tierPass = cb.or(higherTier, sameTierHigherLevel);
                    Join<User, Department> deptJoin =
                    countInterviewerJoin.join("department", JoinType.LEFT);
                    Predicate deptMatch = cb.equal(deptJoin.get("id"), filter.departmentIdForDesignationFilter());
                countPredicates.add(cb.or(cb.equal(countRoot.get("status"), SlotStatus.BOOKED), cb.and(deptMatch, tierPass)));
            }

            countCq.select(cb.countDistinct(countRoot)).where(countPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            Long total = entityManager.createQuery(countCq).getSingleResult();

            // map to DTOs
            var result = new java.util.ArrayList<InterviewerAvailabilityDto>();
            for (AvailabilitySlot s : slots) {
                try { result.add(InterviewerAvailabilityDto.from(s)); } catch (Exception e) { logger.error("Error mapping slot {}: {}", s.getId(), e.getMessage()); }
            }

            return new PagedResult<>(result, total != null ? total : 0L, page, size);
        } catch (Exception e) {
            logger.error("Error in getAllAvailableSlotsPaged: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch paged availability slots", e);
        }
    }

    private List<AvailabilitySlot> filterSlots(AvailabilityFilterDto filter, LocalDateTime from) {
        List<AvailabilitySlot> slots;

        try {
            // ── Step 1: date range ────────────────────────────────────────────
            if (filter.startDateTime() != null && filter.endDateTime() != null) {
                logger.info("Filtering by explicit date range: {} to {}",
                        filter.startDateTime(), filter.endDateTime());
                slots = availabilitySlotRepository.findAllActiveSlotsForHRByDateRange(
                        filter.startDateTime(), filter.endDateTime());
            } else {
                slots = availabilitySlotRepository.findAllActiveSlotsForHR(from);
            }
            logger.info("Step 1 – initial slots: {}", slots.size());

            // ── Step 2: department ────────────────────────────────────────────
            if (filter.departmentIds() != null && !filter.departmentIds().isEmpty()) {
                slots = slots.stream()
                        .filter(slot -> {
                            if (slot.getInterviewer() == null
                                    || slot.getInterviewer().getDepartment() == null)
                                return false;
                            return filter.departmentIds()
                                    .contains(slot.getInterviewer().getDepartment().getId());
                        })
                        .collect(Collectors.toList());
                logger.info("Step 2 – after department filter: {}", slots.size());
            }

            // ── Step 3: domain (priority 1) ─────────────────────────────────
            if (filter.domainIds() != null && !filter.domainIds().isEmpty()) {
                slots = slots.stream()
                        .filter(slot -> {
                            if ("BOOKED".equals(slot.getStatus().name())) return true;
                            if (slot.getInterviewer() == null) return false;

                            var userDomains = slot.getInterviewer().getUserDomains();
                            if (!Hibernate.isInitialized(userDomains)) {
                                Hibernate.initialize(userDomains);
                            }
                            if (userDomains == null || userDomains.isEmpty()) return false;

                            return userDomains.stream()
                                    .filter(ud -> ud != null && ud.getDomain() != null)
                                    .anyMatch(ud -> filter.domainIds()
                                            .contains(ud.getDomain().getId()));
                        })
                        .collect(Collectors.toList());
                logger.info("Step 3 – after domain filter: {}", slots.size());
            }

            // ── Step 4: core technology (priority 2) ───────────────────────
            // AVAILABLE slots filtered by interviewer's core technologies.
            // BOOKED slots always pass — HR must see who is busy even if tech
            // doesn't match the current filter.
            if (filter.technologyIds() != null && !filter.technologyIds().isEmpty()) {
                slots = slots.stream()
                        .filter(slot -> {
                            if ("BOOKED".equals(slot.getStatus().name())) return true;
                            if (slot.getInterviewer() == null) return false;

                            var techs = slot.getInterviewer().getInterviewerTechnologies();
                            if (!Hibernate.isInitialized(techs)) {
                                Hibernate.initialize(techs);
                            }
                            if (techs == null || techs.isEmpty()) return false;

                            return techs.stream()
                                    .filter(it -> it != null
                                            && it.isActive()
                                            && it.isCore()
                                            && it.getTechnology() != null)
                                    .anyMatch(it -> filter.technologyIds()
                                            .contains(it.getTechnology().getId()));
                        })
                        .collect(Collectors.toList());
                logger.info("Step 4 – after core technology filter: {}", slots.size());
            }

            // ── Step 5: years of experience ───────────────────────────────────
            if (filter.minYearsOfExperience() != null) {
                slots = slots.stream()
                        .filter(slot -> {
                            if ("BOOKED".equals(slot.getStatus().name())) return true;
                            if (slot.getInterviewer() == null) return false;
                            Integer years = slot.getInterviewer().getYearsOfExperience();
                            return years != null && years >= filter.minYearsOfExperience();
                        })
                        .collect(Collectors.toList());
                logger.info("Step 5 – after experience filter: {}", slots.size());
            }

            // ── Step 6: tier / level hierarchy ────────────────────────────────
            if (filter.minTierId() != null && filter.departmentIdForDesignationFilter() != null) {
                final int candidateTierOrder  = filter.minTierId().intValue();
                final int candidateLevelOrder = filter.minDesignationLevelInDepartment() != null
                        ? filter.minDesignationLevelInDepartment().intValue()
                        : Integer.MAX_VALUE;

                List<AvailabilitySlot> passedSlots = new ArrayList<>();
                for (AvailabilitySlot slot : slots) {
                    // Always keep booked slots
                    if ("BOOKED".equals(slot.getStatus().name())) {
                        passedSlots.add(slot);
                        continue;
                    }
                    try {
                        if (slot.getInterviewer() == null
                                || slot.getInterviewer().getCurrentDesignation() == null)
                            continue;

                        if (slot.getInterviewer().getDepartment() == null
                                || !slot.getInterviewer().getDepartment().getId()
                                .equals(filter.departmentIdForDesignationFilter()))
                            continue;

                        Designation designation = slot.getInterviewer().getCurrentDesignation();
                        Tier tier = designation.getTier();

                        if (tier == null || tier.getTierOrder() == null || designation.getLevelOrder() == null)
                            continue;

                        int ivTier  = tier.getTierOrder();
                        int ivLevel = designation.getLevelOrder();

                        // UPDATED LOGIC:
                        // 1. Interviewer is in a strictly higher Tier (ivTier < candidateTierOrder)
                        // 2. OR Interviewer is in the SAME Tier but has equal or higher seniority level (ivLevel <= candidateLevelOrder)
                        boolean passes = ivTier < candidateTierOrder
                                || (ivTier == candidateTierOrder && ivLevel <= candidateLevelOrder);

                        if (passes) passedSlots.add(slot);

                    } catch (Exception e) {
                        logger.error("Error checking tier for slot {}: {}", slot.getId(), e.getMessage());
                    }
                }
                slots = passedSlots;
            } else if (filter.minDesignationLevelInDepartment() != null
                    && filter.departmentIdForDesignationFilter() != null) {

                // Level-only branch (no tier filter specified)
                final int candidateLevelOrder = filter.minDesignationLevelInDepartment().intValue();
                slots = slots.stream()
                        .filter(slot -> {
                            if ("BOOKED".equals(slot.getStatus().name())) return true;
                            if (slot.getInterviewer() == null || slot.getInterviewer().getCurrentDesignation() == null)
                                return false;

                            if (slot.getInterviewer().getDepartment() == null
                                    || !slot.getInterviewer().getDepartment().getId()
                                    .equals(filter.departmentIdForDesignationFilter()))
                                return false;

                            Integer ivLevel = slot.getInterviewer().getCurrentDesignation().getLevelOrder();

                            // UPDATED LOGIC: equal or higher seniority (lower or equal numeric value)
                            return ivLevel != null && ivLevel <= candidateLevelOrder;
                        })
                        .collect(Collectors.toList());
            }

            return slots;

        } catch (Exception e) {
            logger.error("Error filtering slots: {}", e.getMessage(), e);
            throw new RuntimeException("Error filtering availability slots", e);
        }
    }

    private boolean shouldPrioritizeMatches(AvailabilityFilterDto filter) {
        return filter != null
                && ((filter.domainIds() != null && !filter.domainIds().isEmpty())
                || (filter.technologyIds() != null && !filter.technologyIds().isEmpty()));
    }

    private List<AvailabilitySlot> sortSlotsByMatchPriority(
            List<AvailabilitySlot> slots,
            AvailabilityFilterDto filter
    ) {
        Comparator<AvailabilitySlot> comparator = Comparator
                .comparing((AvailabilitySlot slot) -> SlotStatus.BOOKED.equals(slot.getStatus()))
                .thenComparing(
                        slot -> countDomainMatches(slot.getInterviewer(), filter.domainIds()),
                        Comparator.reverseOrder()
                )
                .thenComparing(
                        slot -> countCoreTechnologyMatches(slot.getInterviewer(), filter.technologyIds()),
                        Comparator.reverseOrder()
                )
                .thenComparing(
                        AvailabilitySlot::getStartDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );

        return slots.stream().sorted(comparator).collect(Collectors.toList());
    }

    private int countDomainMatches(User interviewer, List<Long> domainIds) {
        if (interviewer == null || domainIds == null || domainIds.isEmpty()) {
            return 0;
        }

        var userDomains = interviewer.getUserDomains();
        if (!Hibernate.isInitialized(userDomains)) {
            Hibernate.initialize(userDomains);
        }
        if (userDomains == null || userDomains.isEmpty()) {
            return 0;
        }

        return (int) userDomains.stream()
                .filter(ud -> ud != null && ud.getDomain() != null)
                .map(ud -> ud.getDomain().getId())
                .filter(domainIds::contains)
                .distinct()
                .count();
    }

    private int countCoreTechnologyMatches(User interviewer, List<Long> technologyIds) {
        if (interviewer == null || technologyIds == null || technologyIds.isEmpty()) {
            return 0;
        }

        var techs = interviewer.getInterviewerTechnologies();
        if (!Hibernate.isInitialized(techs)) {
            Hibernate.initialize(techs);
        }
        if (techs == null || techs.isEmpty()) {
            return 0;
        }

        return (int) techs.stream()
                .filter(it -> it != null
                        && it.isActive()
                        && it.isCore()
                        && it.getTechnology() != null)
                .map(it -> it.getTechnology().getId())
                .filter(technologyIds::contains)
                .distinct()
                .count();
    }

    @Transactional(readOnly = true)
    public InterviewerMatchResponseDto matchInterviewers(InterviewerMatchRequestDto request) {
        if (request == null || request.candidateId() == null) {
            throw new IllegalArgumentException("candidateId is required");
        }

        candidateRepository.findByIdAndIsActiveTrue(request.candidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + request.candidateId()));

        List<CandidateTechnology> candidateTechs =
                candidateTechnologyRepository.findByCandidateIdAndIsActiveTrue(request.candidateId());

        Map<Long, String> candidateCore = new HashMap<>();
        Map<Long, String> candidateNonCore = new HashMap<>();
        for (CandidateTechnology ct : candidateTechs) {
            if (ct == null || ct.getTechnology() == null || ct.getTechnology().getId() == null) continue;
            Long techId = ct.getTechnology().getId();
            String name = ct.getTechnology().getName();
            if (ct.isCore()) {
                candidateCore.put(techId, name);
            } else {
                candidateNonCore.put(techId, name);
            }
        }

        List<Object[]> candidateDomainRows = entityManager.createQuery(
                "SELECT cd.domain.id, cd.domain.name FROM CandidateDomain cd " +
                        "WHERE cd.candidate.id = :candidateId AND cd.domain.isActive = true",
                Object[].class
        ).setParameter("candidateId", request.candidateId()).getResultList();

        Map<Long, String> candidateDomains = new HashMap<>();
        for (Object[] row : candidateDomainRows) {
            candidateDomains.put((Long) row[0], (String) row[1]);
        }

        if (candidateCore.isEmpty() && candidateNonCore.isEmpty() && candidateDomains.isEmpty()) {
            return InterviewerMatchResponseDto.empty();
        }

        Set<Long> allCandidateTechIds = new LinkedHashSet<>();
        allCandidateTechIds.addAll(candidateCore.keySet());
        allCandidateTechIds.addAll(candidateNonCore.keySet());

        Set<Long> interviewerIds = new LinkedHashSet<>();
        if (!allCandidateTechIds.isEmpty()) {
            interviewerIds.addAll(interviewerTechnologyRepository.findInterviewerIdsByTechnologyIds(allCandidateTechIds));
        }
        if (!candidateDomains.isEmpty()) {
            interviewerIds.addAll(userDomainRepository.findUserIdsByDomainIds(candidateDomains.keySet()));
        }
        if (interviewerIds.isEmpty()) {
            return InterviewerMatchResponseDto.empty();
        }

        List<User> interviewers = userRepository.findAllById(interviewerIds).stream()
                .filter(u -> u != null && u.isActive() && u.hasInterviewerRole())
                .filter(u -> matchesOptionalFilters(u, request))
                .collect(Collectors.toList());

        if (interviewers.isEmpty()) {
            logger.info(
                    "Match for candidate {}: 0 interviewers after filters (candidatesWithSkills={}, dept={}, minTier={}, minLevel={})",
                    request.candidateId(),
                    interviewerIds.size(),
                    request.departmentIds(),
                    request.minTierId(),
                    request.minDesignationLevelInDepartment()
            );
            return InterviewerMatchResponseDto.empty();
        }

        logger.info(
                "Match for candidate {}: {} interviewers after filters",
                request.candidateId(),
                interviewers.size()
        );

        List<Long> filteredIds = interviewers.stream().map(User::getId).collect(Collectors.toList());

        Map<Long, List<InterviewerTechnology>> techsByUser = interviewerTechnologyRepository
                .findActiveByInterviewerIdIn(filteredIds)
                .stream()
                .collect(Collectors.groupingBy(it -> it.getInterviewer().getId()));

        Map<Long, List<UserDomain>> domainsByUser = userDomainRepository.findByUserIdIn(filteredIds)
                .stream()
                .collect(Collectors.groupingBy(ud -> ud.getUser().getId()));

        LocalDateTime weekStart = LocalDateTime.now();
        LocalDateTime weekEnd = weekStart.plusDays(7);
        Set<Long> freeInWeek = new HashSet<>(
                availabilitySlotRepository.findInterviewerIdsWithAvailableSlotsBetween(filteredIds, weekStart, weekEnd)
        );

        List<MatchingInterviewerDto> matches = new ArrayList<>();
        for (User interviewer : interviewers) {
            List<InterviewerTechnology> techs = techsByUser.getOrDefault(interviewer.getId(), List.of());
            List<UserDomain> domains = domainsByUser.getOrDefault(interviewer.getId(), List.of());

            Map<Long, String> interviewerAllTechs = new HashMap<>();
            List<String> coreTechnologies = new ArrayList<>();
            List<String> nonCoreTechnologies = new ArrayList<>();
            for (InterviewerTechnology it : techs) {
                if (it.getTechnology() == null || it.getTechnology().getId() == null) continue;
                if (!it.isActive()) continue;
                String techName = it.getTechnology().getName();
                interviewerAllTechs.put(it.getTechnology().getId(), techName);
                if (it.isCore()) {
                    if (!coreTechnologies.contains(techName)) coreTechnologies.add(techName);
                } else if (!nonCoreTechnologies.contains(techName)) {
                    nonCoreTechnologies.add(techName);
                }
            }
            coreTechnologies.sort(String.CASE_INSENSITIVE_ORDER);
            nonCoreTechnologies.sort(String.CASE_INSENSITIVE_ORDER);

            // Match by technology id regardless of interviewer core flag.
            // Keep candidate core/non-core labels for display/ranking.
            List<String> matchedCore = candidateCore.entrySet().stream()
                    .filter(e -> interviewerAllTechs.containsKey(e.getKey()))
                    .map(Map.Entry::getValue)
                    .distinct()
                    .collect(Collectors.toList());
            List<String> matchedNonCore = candidateNonCore.entrySet().stream()
                    .filter(e -> interviewerAllTechs.containsKey(e.getKey()))
                    .map(Map.Entry::getValue)
                    .distinct()
                    .collect(Collectors.toList());

            Map<Long, String> interviewerDomainMap = new HashMap<>();
            for (UserDomain ud : domains) {
                if (ud.getDomain() == null || ud.getDomain().getId() == null) continue;
                if (!ud.getDomain().isActive()) continue;
                interviewerDomainMap.put(ud.getDomain().getId(), ud.getDomain().getName());
            }
            List<String> allDomains = interviewerDomainMap.values().stream()
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            List<String> matchedDomains = candidateDomains.entrySet().stream()
                    .filter(e -> interviewerDomainMap.containsKey(e.getKey()))
                    .map(Map.Entry::getValue)
                    .distinct()
                    .collect(Collectors.toList());

            int techMatchCount = matchedCore.size() + matchedNonCore.size();
            int domainMatchCount = matchedDomains.size();
            if (techMatchCount == 0 && domainMatchCount == 0) continue;

            Integer tierOrder = null;
            Integer levelOrder = null;
            Designation desig = interviewer.getCurrentDesignation();
            if (desig != null) {
                levelOrder = desig.getLevelOrder();
                if (desig.getTier() != null) {
                    tierOrder = desig.getTier().getTierOrder();
                }
            }

            matches.add(new MatchingInterviewerDto(
                    interviewer.getId(),
                    interviewer.getFullName(),
                    interviewer.getEmail(),
                    interviewer.getDepartment() != null ? interviewer.getDepartment().getName() : null,
                    desig != null ? desig.getName() : null,
                    interviewer.getYearsOfExperience(),
                    tierOrder,
                    levelOrder,
                    matchedCore,
                    matchedNonCore,
                    matchedDomains,
                    coreTechnologies,
                    nonCoreTechnologies,
                    allDomains,
                    matchedCore.size(),
                    matchedNonCore.size(),
                    techMatchCount,
                    domainMatchCount,
                    techMatchCount + domainMatchCount,
                    freeInWeek.contains(interviewer.getId())
            ));
        }

        int limit = request.limit() != null && request.limit() > 0 ? request.limit() : DEFAULT_MATCH_LIMIT;

        Comparator<MatchingInterviewerDto> byTechThenDomain = Comparator
                .comparingInt(MatchingInterviewerDto::coreMatchCount).reversed()
                .thenComparing(Comparator.comparingInt(MatchingInterviewerDto::nonCoreMatchCount).reversed())
                .thenComparing(Comparator.comparingInt(MatchingInterviewerDto::domainMatchCount).reversed())
                .thenComparing(m -> m.interviewerName() == null ? "" : m.interviewerName());

        Comparator<MatchingInterviewerDto> byDomainThenTech = Comparator
                .comparingInt(MatchingInterviewerDto::domainMatchCount).reversed()
                .thenComparing(Comparator.comparingInt(MatchingInterviewerDto::coreMatchCount).reversed())
                .thenComparing(Comparator.comparingInt(MatchingInterviewerDto::nonCoreMatchCount).reversed())
                .thenComparing(m -> m.interviewerName() == null ? "" : m.interviewerName());

        // Mutually exclusive: dual matches only in "both", not also in tech/domain lists.
        List<MatchingInterviewerDto> both = matches.stream()
                .filter(m -> m.techMatchCount() > 0 && m.domainMatchCount() > 0)
                .sorted(byTechThenDomain)
                .limit(limit)
                .collect(Collectors.toList());
        List<MatchingInterviewerDto> technologies = matches.stream()
                .filter(m -> m.techMatchCount() > 0 && m.domainMatchCount() == 0)
                .sorted(byTechThenDomain)
                .limit(limit)
                .collect(Collectors.toList());
        List<MatchingInterviewerDto> domains = matches.stream()
                .filter(m -> m.domainMatchCount() > 0 && m.techMatchCount() == 0)
                .sorted(byDomainThenTech)
                .limit(limit)
                .collect(Collectors.toList());

        logger.info(
                "Match groups for candidate {}: both={}, techOnly={}, domainOnly={} | {}",
                request.candidateId(),
                both.size(),
                technologies.size(),
                domains.size(),
                matches.stream()
                        .map(m -> String.format(
                                "%s(#%d tech=%d domain=%d)",
                                m.interviewerName(),
                                m.interviewerId(),
                                m.techMatchCount(),
                                m.domainMatchCount()))
                        .collect(Collectors.joining(", "))
        );

        return new InterviewerMatchResponseDto(both, technologies, domains);
    }

    @Transactional(readOnly = true)
    public List<InterviewerAvailabilityDto> getInterviewerSlots(
            Long interviewerId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        if (interviewerId == null) {
            throw new IllegalArgumentException("interviewerId is required");
        }
        if (startDateTime == null || endDateTime == null) {
            throw new IllegalArgumentException("startDateTime and endDateTime are required");
        }
        if (!endDateTime.isAfter(startDateTime)) {
            throw new IllegalArgumentException("endDateTime must be after startDateTime");
        }

        userRepository.findById(interviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Interviewer not found: " + interviewerId));

        return availabilitySlotRepository
                .findActiveSlotsForInterviewerBetween(interviewerId, startDateTime, endDateTime)
                .stream()
                .filter(slot -> slot.getEndDateTime() != null
                        && slot.getEndDateTime().isAfter(LocalDateTime.now()))
                .map(InterviewerAvailabilityDto::from)
                .collect(Collectors.toList());
    }

    private boolean matchesOptionalFilters(User interviewer, InterviewerMatchRequestDto request) {
        Long interviewerDeptId = interviewer.getDepartment() != null
                ? interviewer.getDepartment().getId()
                : null;

        if (request.departmentIds() != null && !request.departmentIds().isEmpty()) {
            if (interviewerDeptId == null || !request.departmentIds().contains(interviewerDeptId)) {
                return false;
            }
        }

        if (request.minYearsOfExperience() != null) {
            Integer yoe = interviewer.getYearsOfExperience();
            if (yoe == null || yoe < request.minYearsOfExperience()) {
                return false;
            }
        }

        // Same hierarchy as calendar filters: lower tierOrder / levelOrder = more senior.
        // Pass when interviewer is strictly more senior (lower tier), or same tier with
        // equal/higher seniority (levelOrder <= candidate level).
        if (request.minTierId() != null) {
            Designation desig = interviewer.getCurrentDesignation();
            if (desig == null || desig.getTier() == null || desig.getTier().getTierOrder() == null) {
                return false;
            }

            if (request.departmentIdForDesignationFilter() != null) {
                Long designationDeptId = desig.getDepartment() != null
                        ? desig.getDepartment().getId()
                        : interviewerDeptId;
                if (designationDeptId == null
                        || !Objects.equals(designationDeptId, request.departmentIdForDesignationFilter())) {
                    return false;
                }
            }

            int candidateTierOrder = request.minTierId().intValue();
            int interviewerTier = desig.getTier().getTierOrder();

            if (interviewerTier < candidateTierOrder) {
                return true;
            }
            if (interviewerTier > candidateTierOrder) {
                return false;
            }
            if (request.minDesignationLevelInDepartment() == null) {
                return true;
            }
            Integer interviewerLevel = desig.getLevelOrder();
            return interviewerLevel != null
                    && interviewerLevel <= request.minDesignationLevelInDepartment().intValue();
        }

        return true;
    }
}