package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceItemDto;
import ar.edu.utn.frc.siga.allocation.service.ReleasedOccurrenceQueryService;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Paging;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleasedOccurrenceQueryServiceImpl implements ReleasedOccurrenceQueryService {

    private static final Comparator<OccurrenceSlotDto> BY_DATE_TIME_ID = Comparator
            .comparing(OccurrenceSlotDto::date)
            .thenComparing(OccurrenceSlotDto::startTime)
            .thenComparing(OccurrenceSlotDto::occurrenceId);

    private final OccurrenceService occurrenceService;
    private final AcademicEventService academicEventService;
    private final AcademicPeriodService academicPeriodService;
    private final ClassroomService classroomService;
    private final BuildingScopeResolver buildingScopeResolver;

    @Override
    public Page<ReleasedOccurrenceItemDto> findReleased(ReleaseReason reason, LocalDate from, LocalDate to,
                                                        String q, DayOfWeek dayOfWeek, String specialty,
                                                        Pageable pageable) {
        LocalDate effectiveFrom = DateRanges.defaultFrom(from);
        LocalDate effectiveTo = to != null ? to : defaultTo(effectiveFrom);
        DateRanges.requireNotBefore(effectiveTo, effectiveFrom);

        UnallocatedConflictComparator.validate(pageable.getSort());

        List<OccurrenceSlotDto> slots = occurrenceService
                .findSlotsByStatusBetween(OccurrenceStatus.ROOM_RELEASED, effectiveFrom, effectiveTo).stream()
                .filter(o -> reason == null || o.releaseReason() == reason)
                .filter(o -> dayOfWeek == null || o.date().getDayOfWeek() == dayOfWeek)
                .sorted(BY_DATE_TIME_ID)
                .toList();

        Map<Long, AcademicEventResponseDto> eventById = Maps.byId(
                academicEventService.findByIds(ids(slots, OccurrenceSlotDto::eventId)),
                AcademicEventResponseDto::id);
        Map<Long, ClassroomResponseDto> classroomById = Maps.byId(
                classroomService.findByIdsIncludingDeactivated(ids(slots, OccurrenceSlotDto::previousClassroomId)),
                ClassroomResponseDto::id);

        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.CONFLICT_READ);
        Map<GroupKey, List<OccurrenceSlotDto>> groups = slots.stream()
                .filter(o -> eventById.containsKey(o.eventId()))
                .filter(o -> AcademicEventMatcher.matches(eventById.get(o.eventId()), q, null, specialty))
                .filter(o -> o.previousClassroomId() == null || !classroomById.containsKey(o.previousClassroomId())
                        || scope.allows(classroomById.get(o.previousClassroomId()).buildingId()))
                .collect(Collectors.groupingBy(
                        o -> new GroupKey(o.eventId(), o.releaseReason(), o.releaseNote(), o.previousClassroomId()),
                        LinkedHashMap::new, Collectors.toList()));

        List<ReleasedOccurrenceItemDto> items = groups.entrySet().stream()
                .map(e -> toItem(e.getKey(), e.getValue(), eventById, classroomById))
                .sorted(UnallocatedConflictComparator.of(pageable.getSort(), ReleasedOccurrenceItemDto::event))
                .toList();

        log.info("Ocurrencias liberadas listadas: reason={}, eventos={}", reason, items.size());
        return Paging.of(items, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }

    private record GroupKey(Long eventId, ReleaseReason reason, String note, Long previousClassroomId) {
    }

    private static ReleasedOccurrenceItemDto toItem(GroupKey key, List<OccurrenceSlotDto> slots,
            Map<Long, AcademicEventResponseDto> eventById, Map<Long, ClassroomResponseDto> classroomById) {
        List<ReleasedOccurrenceItemDto.ReleasedDate> dates = slots.stream()
                .map(o -> new ReleasedOccurrenceItemDto.ReleasedDate(o.occurrenceId(), o.date()))
                .toList();
        return new ReleasedOccurrenceItemDto(eventById.get(key.eventId()), key.reason(), key.note(),
                classroomById.get(key.previousClassroomId()), dates);
    }

    private static Set<Long> ids(List<OccurrenceSlotDto> slots, java.util.function.Function<OccurrenceSlotDto, Long> id) {
        return slots.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private LocalDate defaultTo(LocalDate from) {
        return academicPeriodService.findActive().stream()
                .map(AcademicPeriodResponseDto::endDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> from.plusMonths(6));
    }
}
