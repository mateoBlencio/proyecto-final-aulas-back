package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeCommand;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleDto;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadConflictException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncLock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Resolución humana de un horario en revisión. Sin transacción externa: la operación de {@code allocation} y el
 * cierre del horario son transacciones separadas, y el lock del job evita que el job envíe entre las dos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadReviewService {

    private static final Set<Integer> SENTINEL_ROOM_NUMBERS = Set.of(999, 0);

    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadScheduleStateWriter stateWriter;
    private final SysacadSyncLock syncLock;
    private final ClassroomService classroomService;
    private final AllocationService allocationService;

    public SysacadScheduleDto resendSiga(Long scheduleId, String user) {
        return resolve(scheduleId, schedule -> stateWriter.resolveResendSiga(scheduleId, user));
    }

    public SysacadScheduleDto keepSysacad(Long scheduleId, String user, String observation) {
        return resolve(scheduleId, schedule -> {
            Integer building = SysacadScheduleStateWriter.lastReadBuilding(schedule);
            Integer room = SysacadScheduleStateWriter.lastReadRoom(schedule);
            Long classroomId = SENTINEL_ROOM_NUMBERS.contains(room) ? null
                    : classroomService.findByRoomNumberAndBuildingCode(room, building)
                            .map(ClassroomResponseDto::id)
                            .orElseThrow(() -> new SysacadConflictException("Aula desconocida en SIGA",
                                    "SIGA no tiene el aula " + room + " del edificio " + building
                                            + ": no se puede quedar con lo de SysAcad."));
            Set<Long> eventIds = Stream.of(schedule.getEventId1(), schedule.getEventId2())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<AffectedOccurrence> annulled = List.of();
            if (!eventIds.isEmpty()) {
                SysacadDirectChangeResult result = SystemScope.call(() -> allocationService.applySysacadDirectChange(
                        new SysacadDirectChangeCommand(eventIds, classroomId, observation, user, true)));
                if (!(result instanceof SysacadDirectChangeResult.Applied applied)) {
                    throw new IllegalStateException("El cambio con reglas manuales no devuelve aula ocupada");
                }
                annulled = applied.changes().stream()
                        .filter(change -> change.fromClassroomId() != null)
                        .map(change -> new AffectedOccurrence(change.occurrenceId(), change.date(),
                                change.fromClassroomId()))
                        .toList();
            }
            stateWriter.resolveKeepSysacad(scheduleId, user, annulled);
        });
    }

    private SysacadScheduleDto resolve(Long scheduleId, Consumer<SysacadSchedule> action) {
        if (!syncLock.tryLock()) {
            throw new SysacadConflictException("Sincronización en curso",
                    "Está corriendo el job de envío o el sync de SysAcad. Reintentá en unos minutos.");
        }
        try {
            SysacadSchedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> ResourceNotFoundException.of("SysacadSchedule", scheduleId));
            if (schedule.getStatus() != SysacadScheduleStatus.REVIEW) {
                throw new SysacadConflictException("Horario fuera de revisión",
                        "El horario " + scheduleId + " no está en revisión.");
            }
            action.accept(schedule);
        } finally {
            syncLock.unlock();
        }
        SysacadSchedule resolved = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ResourceNotFoundException.of("SysacadSchedule", scheduleId));
        log.info("Horario {} resuelto de revisión", scheduleId);
        return toDto(resolved);
    }

    private static SysacadScheduleDto toDto(SysacadSchedule s) {
        return new SysacadScheduleDto(s.getId(), s.getCourseCode(), s.getSubject(), s.getCommission(),
                s.getDayOfWeek(), s.getSemester(), s.getStatus().name(), s.getKnownBuilding(), s.getKnownRoom(),
                s.getBaseBuilding(), s.getBaseRoom(), s.getDifferentBuilding(), s.getDifferentRoom(),
                s.getReviewReason() == null ? null : s.getReviewReason().name(), s.getReviewSince());
    }
}
