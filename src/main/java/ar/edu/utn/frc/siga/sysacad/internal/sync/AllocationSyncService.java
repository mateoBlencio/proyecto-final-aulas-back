package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.util.RecurringEventKey;
import ar.edu.utn.frc.siga.events.dto.response.SysacadRecurringEventRefDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.write.DirectChangeHandler;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class AllocationSyncService implements SysacadViewSyncer {

    private static final Set<Integer> SENTINEL_ROOM_NUMBERS = Set.of(999, 0);

    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final AcademicEventService academicEventService;
    private final ClassroomService classroomService;
    private final AllocationService allocationService;
    private final SysacadSyncStateService syncStateService;
    private final SysacadScheduleLoader scheduleLoader;
    private final DirectChangeHandler directChangeHandler;
    private final SysacadProperties properties;

    @Override
    public SysacadView view() {
        return SysacadView.ASIGNACIONES;
    }

    @Override
    public void sync(SysacadCatalogReader catalog) {
        ViewSyncRunner.run(syncStateService, SysacadView.ASIGNACIONES, "Asignaciones", log, () -> doSync(catalog));
    }

    private int doSync(SysacadCatalogReader catalog) {
        SysacadCommissionResolver resolver = new SysacadCommissionResolver(commissionService, subjectCommissionService);
        Map<ClassroomKey, Optional<ClassroomResponseDto>> classroomCache = new HashMap<>();
        Map<OverlapKey, List<OverlapEntry>> overlapsBySlot = new LinkedHashMap<>();
        Map<Long, Long> classroomIdByEvent = new LinkedHashMap<>();
        int conflictingRooms = 0;
        boolean writeOn = properties.getWrite().mode() == SysacadWriteMode.ON;
        Map<SysacadScheduleLoader.ScheduleKey, SysacadScheduleLoader.BaseRoom> baseRooms =
                writeOn ? scheduleLoader.findBaseRooms() : Map.of();

        Map<RecurringEventKey, Long> eventIdsByKey = academicEventService.findSysacadRecurringEvents().stream()
                .collect(Collectors.toMap(AllocationSyncService::keyOf, SysacadRecurringEventRefDto::eventId,
                        (first, ignored) -> first));

        List<SysacadScheduleLoader.ResolvedScheduleRow> scheduleRows = new ArrayList<>();
        Set<Long> keylessEventIds = new TreeSet<>();
        int currentYear = LocalDate.now().getYear();

        for (SysacadAllocationDto row : catalog.findAllocations()) {
            List<TermType> termTypes = SysacadCommissionResolver.termTypes(
                    row.semester(), row.courseCode(), row.subjectCode());
            if (row.commissionNumber() == null) {
                log.warn("Fila de Horarios sin número de comisión para curso={}, materia={}: no se registra el horario",
                        row.courseCode(), row.subjectCode());
            }
            boolean hasKey = row.commissionNumber() != null && !termTypes.isEmpty() && row.specialtyCode() != null
                    && row.studyPlanCode() != null && row.subjectCode() != null;

            Optional<SysacadCommissionResolver.ResolvedLink> resolved =
                    resolver.resolve(row.courseCode(), row.subjectCode(), row.specialtyCode(),
                            row.studyPlanCode());
            if (resolved.isEmpty()) {
                if (hasKey) {
                    scheduleRows.add(new SysacadScheduleLoader.ResolvedScheduleRow(
                            row, currentYear, Set.of(), NotSendableReason.COMMISSION_NOT_RESOLVED));
                }
                continue;
            }
            CommissionResponseDto commission = resolved.get().commission();
            SubjectCommissionResponseDto link = resolved.get().link();
            int year = commission.academicPeriod().year();
            Integer building = row.buildingCode();
            Integer room = row.roomNumber();
            SysacadScheduleLoader.BaseRoom base = hasKey
                    ? baseRooms.get(SysacadScheduleLoader.ScheduleKey.of(year, row)) : null;
            if (base != null) {
                building = base.building();
                room = base.room();
            }
            NotSendableReason skipReason = null;
            Optional<ClassroomResponseDto> classroom = Optional.empty();
            if (!isSentinel(room)) {
                classroom = resolveClassroom(classroomCache, room, building);
                if (classroom.isEmpty()) {
                    log.warn("No se pudo resolver el aula de SysAcad (aula={}, edificio={}) para curso={}, "
                                    + "materia={}: fila de asignación salteada",
                            room, building, row.courseCode(), row.subjectCode());
                    skipReason = NotSendableReason.ROOM_UNKNOWN;
                }
            }
            if (base != null && skipReason == null && !isSentinel(row.roomNumber())
                    && resolveClassroom(classroomCache, row.roomNumber(), row.buildingCode()).isEmpty()) {
                skipReason = NotSendableReason.ROOM_UNKNOWN;
            }

            Set<Long> eventIds = new TreeSet<>();
            for (TermType termType : termTypes) {
                Long eventId = eventIdsByKey.get(new RecurringEventKey(
                        link.subjectId(), commission.id(), row.dayOfWeek(), row.startTime(),
                        termType.startDate(year), termType.endDate(year)));
                if (eventId == null) {
                    if (classroom.isPresent()) {
                        log.warn("No se encontró el evento ya creado por EVENTOS para curso={}, materia={}, "
                                        + "dia={}, hora={}, cuatrimestre={}: fila de asignación salteada",
                                row.courseCode(), row.subjectCode(), row.dayOfWeek(), row.startTime(), termType);
                    }
                    if (skipReason == null) {
                        skipReason = NotSendableReason.EVENT_NOT_FOUND;
                    }
                    continue;
                }
                eventIds.add(eventId);
                if (classroom.isEmpty()) {
                    continue;
                }

                Long classroomId = classroom.get().id();
                Long assignedClassroomId = classroomIdByEvent.putIfAbsent(eventId, classroomId);
                if (assignedClassroomId != null && !assignedClassroomId.equals(classroomId)) {
                    conflictingRooms++;
                    log.warn("Fila de Horarios con el mismo evento y otra aula: curso={}, materia={}, dia={}, hora={}, "
                                    + "cuatrimestre={}, aula={}, edificio={}: se conserva la primera aula y se saltea la fila",
                            row.courseCode(), row.subjectCode(), row.dayOfWeek(), row.startTime(), termType,
                            row.roomNumber(), row.buildingCode());
                    if (skipReason == null) {
                        skipReason = NotSendableReason.EVENT_ROOM_CONFLICT;
                    }
                    continue;
                }

                recordOverlap(overlapsBySlot, row, building, room, termType, eventId);
            }
            if (hasKey) {
                scheduleRows.add(new SysacadScheduleLoader.ResolvedScheduleRow(row, year, eventIds, skipReason));
            } else {
                keylessEventIds.addAll(eventIds);
            }
        }

        warnOverlaps(overlapsBySlot);
        if (conflictingRooms > 0) {
            log.warn("Sync de asignaciones: {} filas salteadas por asignar el mismo evento a más de un aula",
                    conflictingRooms);
        }

        List<AllocationItem> items = classroomIdByEvent.entrySet().stream()
                .map(entry -> new AllocationItem(new AllocationTarget.Event(entry.getKey()), entry.getValue()))
                .toList();
        SysacadScheduleLoader.LoadResult loadResult = scheduleLoader.load(scheduleRows);
        Set<Long> eventIdsToUpdate = new TreeSet<>(loadResult.eventIdsToUpdate());
        if (!writeOn) {
            eventIdsToUpdate.addAll(keylessEventIds);
        }
        int affected = SystemScope.call(() -> allocationService.syncFromSysacad(items, eventIdsToUpdate));
        handleDirectChanges(loadResult);
        return affected;
    }

    private void handleDirectChanges(SysacadScheduleLoader.LoadResult loadResult) {
        for (Long scheduleId : loadResult.directChangeScheduleIds()) {
            try {
                directChangeHandler.handle(scheduleId);
            } catch (RuntimeException e) {
                log.error("No se pudo procesar el cambio directo del horario {}", scheduleId, e);
            }
        }
        for (SysacadScheduleLoader.DryRunChange change : loadResult.dryRunChanges()) {
            try {
                directChangeHandler.recordDryRun(change.scheduleId(), change.fromBuilding(), change.fromRoom(),
                        change.toBuilding(), change.toRoom());
            } catch (RuntimeException e) {
                log.error("No se pudo registrar el cambio directo simulado del horario {}", change.scheduleId(), e);
            }
        }
    }

    private static boolean isSentinel(Integer roomNumber) {
        return roomNumber != null && SENTINEL_ROOM_NUMBERS.contains(roomNumber);
    }

    private static RecurringEventKey keyOf(SysacadRecurringEventRefDto ref) {
        return new RecurringEventKey(ref.subjectId(), ref.commissionId(), ref.dayOfWeek(), ref.startTime(),
                ref.startDate(), ref.endDate());
    }

    private Optional<ClassroomResponseDto> resolveClassroom(
            Map<ClassroomKey, Optional<ClassroomResponseDto>> cache, Integer roomNumber, Integer buildingCode) {
        return cache.computeIfAbsent(new ClassroomKey(roomNumber, buildingCode),
                key -> classroomService.findByRoomNumberAndBuildingCode(key.roomNumber(), key.buildingCode()));
    }

    private void recordOverlap(Map<OverlapKey, List<OverlapEntry>> overlapsBySlot, SysacadAllocationDto row,
            Integer building, Integer room, TermType termType, Long eventId) {
        OverlapKey key = new OverlapKey(building, room, row.dayOfWeek(), row.startTime(), termType);
        overlapsBySlot.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new OverlapEntry(row.courseCode(), row.subjectCode(), eventId));
    }

    private void warnOverlaps(Map<OverlapKey, List<OverlapEntry>> overlapsBySlot) {
        overlapsBySlot.forEach((key, entries) -> {
            if (entries.size() > 1) {
                log.warn("Solapamiento de aula detectado en SysAcad: edificio={}, aula={}, dia={}, hora={}, "
                                + "cuatrimestre={} -> {} filas comparten el mismo slot: {}",
                        key.buildingCode(), key.roomNumber(), key.dayOfWeek(), key.startTime(), key.termType(),
                        entries.size(), entries);
            }
        });
    }

    private record ClassroomKey(Integer roomNumber, Integer buildingCode) {
    }

    private record OverlapKey(Integer buildingCode, Integer roomNumber, DayOfWeek dayOfWeek, LocalTime startTime,
            TermType termType) {
    }

    private record OverlapEntry(String courseCode, Integer subjectCode, Long eventId) {
    }
}
