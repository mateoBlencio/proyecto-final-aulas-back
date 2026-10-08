package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Label of an allocation: "Aula 101, Central · 04/03/2026". Reads the classroom and occurrence ids from the audited
 * state, so allocations deleted by a regeneration are labelled too. Deactivated classrooms are included. If the
 * occurrence was deleted, the label is partial (room only): this module cannot read another module's audit tables
 * to recover the date.
 */
@Component
@RequiredArgsConstructor
class AllocationAuditLabelProvider implements AuditLabelProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClassroomService classroomService;
    private final OccurrenceService occurrenceService;

    @Override
    public Class<?> entityType() {
        return Allocation.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> classroomIds = records.stream().map(record -> record.longValue("classroomId"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> occurrenceIds = records.stream().map(record -> record.longValue("occurrenceId"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ClassroomResponseDto> classrooms = classroomIds.isEmpty() ? new HashMap<>()
                : Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);
        Map<Long, LocalDate> dates = occurrenceIds.isEmpty() ? new HashMap<>()
                : occurrenceService.findDatesByIds(occurrenceIds);

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            ClassroomResponseDto classroom = classrooms.get(record.longValue("classroomId"));
            LocalDate occurrenceDate = dates.get(record.longValue("occurrenceId"));
            String room = classroom == null ? null
                    : "Aula " + classroom.roomNumber() + ", " + classroom.buildingName();
            String date = occurrenceDate == null ? null : occurrenceDate.format(DATE);
            String label = room == null ? date : date == null ? room : room + " · " + date;
            if (label != null) {
                labels.put(record.recordId(), label);
            }
        }
        return labels;
    }
}
