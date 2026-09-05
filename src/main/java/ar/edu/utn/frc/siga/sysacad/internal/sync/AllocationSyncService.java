package ar.edu.utn.frc.siga.sysacad.internal.sync;

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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        Set<AllocationItem> items = new LinkedHashSet<>();

        Map<RecurringEventKey, Long> eventIdsByKey = academicEventService.findSysacadRecurringEvents().stream()
                .collect(Collectors.toMap(AllocationSyncService::keyOf, SysacadRecurringEventRefDto::eventId));

        for (SysacadAllocationDto row : catalog.findAllocations()) {
            if (isSentinel(row)) {
                continue;
            }

            Optional<SysacadCommissionResolver.ResolvedLink> resolved =
                    resolver.resolve(row.courseCode(), row.subjectCode());
            if (resolved.isEmpty()) {
                continue;
            }
            CommissionResponseDto commission = resolved.get().commission();
            SubjectCommissionResponseDto link = resolved.get().link();
            Optional<ClassroomResponseDto> classroom =
                    resolveClassroom(classroomCache, row.roomNumber(), row.buildingCode());
            if (classroom.isEmpty()) {
                log.warn("No se pudo resolver el aula de SysAcad (aula={}, edificio={}) para curso={}, "
                                + "materia={}: fila de asignación salteada",
                        row.roomNumber(), row.buildingCode(), row.courseCode(), row.subjectCode());
                continue;
            }

            int year = commission.academicPeriod().year();
            for (TermType termType : SysacadCommissionResolver.termTypes(
                    row.semester(), row.courseCode(), row.subjectCode())) {
                Long eventId = eventIdsByKey.get(new RecurringEventKey(
                        link.subjectId(), commission.id(), row.dayOfWeek(), row.startTime(),
                        termType.startDate(year), termType.endDate(year)));
                if (eventId == null) {
                    log.warn("No se encontró el evento ya creado por EVENTOS para curso={}, materia={}, "
                                    + "dia={}, hora={}, cuatrimestre={}: fila de asignación salteada",
                            row.courseCode(), row.subjectCode(), row.dayOfWeek(), row.startTime(), termType);
                    continue;
                }

                recordOverlap(overlapsBySlot, row, termType, eventId);
                items.add(new AllocationItem(new AllocationTarget.Event(eventId), classroom.get().id()));
            }
        }

        warnOverlaps(overlapsBySlot);

        return allocationService.syncFromSysacad(List.copyOf(items));
    }

    private static boolean isSentinel(SysacadAllocationDto row) {
        return row.roomNumber() != null && SENTINEL_ROOM_NUMBERS.contains(row.roomNumber());
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
            TermType termType, Long eventId) {
        OverlapKey key = new OverlapKey(row.buildingCode(), row.roomNumber(), row.dayOfWeek(), row.startTime(), termType);
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
