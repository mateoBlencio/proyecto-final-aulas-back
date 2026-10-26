package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSearchOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSearchResultDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSearchRow;
import ar.edu.utn.frc.siga.allocation.repository.AllocationSearchRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationSearchService;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.search.LikePatterns;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllocationSearchServiceImpl implements AllocationSearchService {

    private static final int MIN_QUERY_LENGTH = 2;

    private final AcademicPeriodService academicPeriodService;
    private final BuildingScopeResolver buildingScopeResolver;
    private final AllocationSearchRepository repository;

    @Override
    public List<AllocationSearchResultDto> search(String q, LocalDate date, int limit) {
        String text = q.strip();
        if (text.length() < MIN_QUERY_LENGTH) {
            throw new InvalidSelectionException("La búsqueda necesita al menos 2 caracteres");
        }

        List<AcademicPeriodResponseDto> periods = academicPeriodService.findActive().stream()
                .filter(p -> p.startDate() != null && p.endDate() != null)
                .filter(p -> !date.isBefore(p.startDate()) && !date.isAfter(p.endDate()))
                .toList();
        if (periods.isEmpty()) {
            return List.of();
        }
        LocalDate from = periods.stream().map(AcademicPeriodResponseDto::startDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = periods.stream().map(AcademicPeriodResponseDto::endDate)
                .max(Comparator.naturalOrder()).orElseThrow();

        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ);
        if (!scope.isUnrestricted() && scope.buildingIds().isEmpty()) {
            return List.of();
        }

        Map<Long, List<AllocationSearchRow>> byEvent = new LinkedHashMap<>();
        for (AllocationSearchRow row : repository.search(LikePatterns.containing(text), date, from, to, limit, scope)) {
            byEvent.computeIfAbsent(row.eventId(), id -> new ArrayList<>()).add(row);
        }
        return byEvent.values().stream().map(AllocationSearchServiceImpl::toResult).toList();
    }

    private static AllocationSearchResultDto toResult(Collection<AllocationSearchRow> rows) {
        AllocationSearchRow first = rows.iterator().next();
        List<AllocationSearchOccurrenceDto> occurrences = rows.stream()
                .map(r -> new AllocationSearchOccurrenceDto(r.allocationId(), r.occurrenceId(), r.date(),
                        r.startTime(), r.startTime().plusMinutes(r.durationMinutes()), r.classroomId(),
                        String.valueOf(r.classroomNumber()), r.buildingId()))
                .toList();
        return new AllocationSearchResultDto(first.eventId(), first.eventType(), first.uniqueEventType(),
                first.subject(), first.section(), first.specialtyName(), occurrences);
    }
}
