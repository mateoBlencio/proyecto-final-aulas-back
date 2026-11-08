package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadScheduleLoader {

    private static final int MAX_EVENTS_PER_SCHEDULE = 2;

    private final SysacadScheduleRepository repository;
    private final SysacadViewReconciler reconciler;
    private final SysacadProperties properties;

    public record ResolvedScheduleRow(SysacadAllocationDto row, Integer year, Set<Long> eventIds,
            NotSendableReason skipReason) {
    }

    public record LoadResult(Set<Long> eventIdsToUpdate, List<Long> directChangeScheduleIds,
            List<DryRunChange> dryRunChanges) {
    }

    public record DryRunChange(Long scheduleId, Integer fromBuilding, Integer fromRoom, Integer toBuilding,
            Integer toRoom) {
    }

    public record BaseRoom(Integer building, Integer room) {
    }

    public record ScheduleKey(Integer year, Integer specialty, Integer plan, Integer subject, Integer commission,
            Integer dayOfWeek, Integer semester) {

        public static ScheduleKey of(Integer year, SysacadAllocationDto row) {
            return new ScheduleKey(year, row.specialtyCode(), row.studyPlanCode(), row.subjectCode(),
                    row.commissionNumber(), row.dayOfWeek().getValue(), row.semester());
        }
    }

    private record RowContent(LocalTime startTime, LocalTime endTime, Integer durationMinutes, Integer building,
            Integer room, String courseCode) {
    }

    @Transactional(readOnly = true)
    public Map<ScheduleKey, BaseRoom> findBaseRooms() {
        Map<ScheduleKey, BaseRoom> bases = new HashMap<>();
        for (SysacadSchedule schedule : repository.findByBaseBuildingIsNotNullAndBaseRoomIsNotNull()) {
            bases.put(keyOf(schedule), new BaseRoom(schedule.getBaseBuilding(), schedule.getBaseRoom()));
        }
        return bases;
    }

    @Transactional
    public LoadResult load(List<ResolvedScheduleRow> rows) {
        Instant now = Instant.now();
        SysacadWriteMode mode = properties.getWrite().mode();
        Set<Long> eventIdsToUpdate = new TreeSet<>();
        List<Long> directChangeScheduleIds = new ArrayList<>();
        List<DryRunChange> dryRunChanges = new ArrayList<>();
        Map<ScheduleKey, List<ResolvedScheduleRow>> groups = new LinkedHashMap<>();
        for (ResolvedScheduleRow resolved : rows) {
            groups.computeIfAbsent(keyOf(resolved), k -> new ArrayList<>()).add(resolved);
        }

        Map<ScheduleKey, SysacadSchedule> existing = new HashMap<>();
        for (SysacadSchedule schedule : repository.findAll()) {
            existing.put(keyOf(schedule), schedule);
        }

        List<SysacadSchedule> created = new ArrayList<>();
        groups.forEach((key, group) -> {
            SysacadSchedule schedule = existing.get(key);
            if (schedule == null) {
                schedule = newSchedule(key);
                created.add(schedule);
            }
            ApplyResult result = apply(schedule, group, now);
            if (result.directChange()) {
                directChangeScheduleIds.add(schedule.getId());
            }
            if (result.dryRunChange() != null) {
                dryRunChanges.add(result.dryRunChange());
            }
            if (result.followView() && mode != SysacadWriteMode.ON) {
                eventIdsToUpdate.addAll(result.eventIds());
            }
        });
        repository.saveAll(created);

        if (!groups.isEmpty()) {
            Set<ScheduleKey> loaded = groups.keySet();
            existing.forEach((key, schedule) -> {
                if (!loaded.contains(key)) {
                    schedule.setPresentInView(false);
                }
            });
        }
        return new LoadResult(eventIdsToUpdate, directChangeScheduleIds, dryRunChanges);
    }

    private record ApplyResult(Set<Long> eventIds, boolean followView, boolean directChange,
            DryRunChange dryRunChange) {
    }

    private ApplyResult apply(SysacadSchedule schedule, List<ResolvedScheduleRow> group, Instant now) {
        SysacadAllocationDto first = group.get(0).row();
        Set<RowContent> contents = new HashSet<>();
        Set<Long> eventIds = new TreeSet<>();
        for (ResolvedScheduleRow resolved : group) {
            contents.add(contentOf(resolved.row()));
            eventIds.addAll(resolved.eventIds());
        }
        boolean keyConflict = contents.size() > 1;
        boolean tooManyEvents = eventIds.size() > MAX_EVENTS_PER_SCHEDULE;

        NotSendableReason reason = null;
        if (keyConflict) {
            reason = NotSendableReason.KEY_CONFLICT;
        } else if (tooManyEvents) {
            reason = NotSendableReason.TOO_MANY_EVENTS;
        } else {
            reason = group.stream().map(ResolvedScheduleRow::skipReason).filter(Objects::nonNull).findFirst()
                    .orElse(null);
        }
        if (keyConflict) {
            log.warn("Horario de SysAcad con filas distintas para la misma clave (año={}, especialidad={}, plan={}, "
                            + "materia={}, comisión={}, día={}, cuatrimestre={}): no enviable",
                    schedule.getYear(), schedule.getSpecialty(), schedule.getPlan(), schedule.getSubject(),
                    schedule.getCommission(), schedule.getDayOfWeek(), schedule.getSemester());
        }

        boolean followView = keyConflict;
        boolean directChange = false;
        DryRunChange dryRunChange = null;
        if (!keyConflict) {
            schedule.setCourseCode(first.courseCode());
            schedule.setStartTime(first.startTime());
            schedule.setEndTime(first.endTime());
            schedule.setDurationMinutes(first.durationMinutes());
            if (schedule.getKnownRoom() == null) {
                schedule.setKnownBuilding(first.buildingCode());
                schedule.setKnownRoom(first.roomNumber());
                schedule.setBaseBuilding(first.buildingCode());
                schedule.setBaseRoom(first.roomNumber());
                followView = true;
            } else {
                Integer previousBuilding = schedule.getKnownBuilding();
                Integer previousRoom = schedule.getKnownRoom();
                ReconcileOutcome outcome = reconciler.reconcile(schedule, first.buildingCode(), first.roomNumber(),
                        now);
                // Conocido igual a base repara una propagación fallida en el sync anterior; distinto es un puntual de SIGA.
                followView = Objects.equals(schedule.getKnownBuilding(), schedule.getBaseBuilding())
                        && Objects.equals(schedule.getKnownRoom(), schedule.getBaseRoom());
                directChange = outcome == ReconcileOutcome.DIRECT_CHANGE;
                if (outcome == ReconcileOutcome.EXTERNAL_CHANGE_APPLIED
                        && properties.getWrite().mode() == SysacadWriteMode.DRY_RUN) {
                    dryRunChange = new DryRunChange(schedule.getId(), previousBuilding, previousRoom,
                            first.buildingCode(), first.roomNumber());
                }
            }
        }

        List<Long> linked = eventIds.stream().limit(MAX_EVENTS_PER_SCHEDULE).toList();
        schedule.setEventId1(linked.isEmpty() ? null : linked.get(0));
        schedule.setEventId2(linked.size() > 1 ? linked.get(1) : null);
        schedule.setSendable(reason == null);
        schedule.setNotSendableReason(reason);
        schedule.setPresentInView(true);
        schedule.setLastReadAt(now);
        return new ApplyResult(eventIds, followView, directChange, dryRunChange);
    }

    private static SysacadSchedule newSchedule(ScheduleKey key) {
        return SysacadSchedule.builder()
                .year(key.year())
                .specialty(key.specialty())
                .plan(key.plan())
                .subject(key.subject())
                .commission(key.commission())
                .dayOfWeek(key.dayOfWeek())
                .semester(key.semester())
                .build();
    }

    private static RowContent contentOf(SysacadAllocationDto row) {
        return new RowContent(row.startTime(), row.endTime(), row.durationMinutes(), row.buildingCode(),
                row.roomNumber(), row.courseCode());
    }

    private static ScheduleKey keyOf(ResolvedScheduleRow resolved) {
        return ScheduleKey.of(resolved.year(), resolved.row());
    }

    private static ScheduleKey keyOf(SysacadSchedule schedule) {
        return new ScheduleKey(schedule.getYear(), schedule.getSpecialty(), schedule.getPlan(),
                schedule.getSubject(), schedule.getCommission(), schedule.getDayOfWeek(), schedule.getSemester());
    }
}
