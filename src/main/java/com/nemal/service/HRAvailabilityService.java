package com.nemal.service;

import com.nemal.dto.AvailabilityFilterDto;
import com.nemal.dto.InterviewerAvailabilityDto;
import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.Designation;
import com.nemal.entity.Tier;
import com.nemal.repository.AvailabilitySlotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HRAvailabilityService {

    private static final Logger logger = LoggerFactory.getLogger(HRAvailabilityService.class);

    /**
     * How far back (in days) the HR calendar shows past slots.
     * 30 days keeps recent booked/completed interviews visible for audit.
     */
    private static final int HR_LOOKBACK_DAYS = 30;

    private final AvailabilitySlotRepository availabilitySlotRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public HRAvailabilityService(AvailabilitySlotRepository availabilitySlotRepository) {
        this.availabilitySlotRepository = availabilitySlotRepository;
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

            // ── Step 3: technology ────────────────────────────────────────────
            // AVAILABLE slots filtered by interviewer's active technologies.
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
                                            && it.getTechnology() != null)
                                    .anyMatch(it -> filter.technologyIds()
                                            .contains(it.getTechnology().getId()));
                        })
                        .collect(Collectors.toList());
                logger.info("Step 3 – after technology filter: {}", slots.size());
            }

            // ── Step 4: years of experience ───────────────────────────────────
            if (filter.minYearsOfExperience() != null) {
                slots = slots.stream()
                        .filter(slot -> {
                            if ("BOOKED".equals(slot.getStatus().name())) return true;
                            if (slot.getInterviewer() == null) return false;
                            Integer years = slot.getInterviewer().getYearsOfExperience();
                            return years != null && years >= filter.minYearsOfExperience();
                        })
                        .collect(Collectors.toList());
                logger.info("Step 4 – after experience filter: {}", slots.size());
            }

            // ── Step 5: tier / level hierarchy ────────────────────────────────
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
}