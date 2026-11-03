package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.model.AutogestionNotifiedRoom;
import ar.edu.utn.frc.siga.sysacad.internal.repository.AutogestionNotifiedRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares the classrooms the enabled commission has now with the ones last notified and sends one message with
 * the differences that fall in the next {@value #WINDOW_DAYS} days. A release is never notified: it keeps the
 * last notified classroom as baseline. The first run only records the baseline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"siga.sysacad.enabled", "siga.sysacad.autogestion.enabled"}, havingValue = "true")
public class AutogestionRoomCheck {

    static final int WINDOW_DAYS = 7;

    private final SysacadCommissionMessageService messageService;
    private final SysacadProperties properties;
    private final CommissionService commissionService;
    private final SubjectService subjectService;
    private final AcademicEventService academicEventService;
    private final AllocationService allocationService;
    private final ClassroomService classroomService;
    private final AutogestionNotifiedRoomRepository notifiedRepository;

    public void check(LocalDate today) {
        SysacadProperties.Autogestion target = properties.getAutogestion();
        CommissionResponseDto commission = commissionService.findActiveByCourseCode(target.courseCode());
        List<SubjectResponseDto> subjects = subjectService.findAll(
                new SubjectFilter(target.subject(), null, target.specialty(), null), PageRequest.of(0, 2), false)
                .getContent();
        if (subjects.size() != 1) {
            log.warn("Chequeo de autogestión omitido: {} materias coinciden con especialidad={} materia={}",
                    subjects.size(), target.specialty(), target.subject());
            return;
        }

        List<OccurrenceResponseDto> occurrences = academicEventService
                .findClassOccurrences(subjects.getFirst().id(), commission.id(), today.plusDays(1));
        Map<Long, Long> currentRooms = currentRooms(occurrences);
        Map<Long, AutogestionNotifiedRoom> notified = notifiedRepository
                .findByOccurrenceIdIn(occurrences.stream().map(OccurrenceResponseDto::id).toList()).stream()
                .collect(Collectors.toMap(AutogestionNotifiedRoom::getOccurrenceId, Function.identity()));
        boolean firstRun = notifiedRepository.count() == 0;

        List<AutogestionNotifiedRoom> baselines = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        for (OccurrenceResponseDto occurrence : occurrences) {
            Long current = currentRooms.get(occurrence.id());
            AutogestionNotifiedRoom row = notified.get(occurrence.id());
            if (firstRun) {
                baselines.add(row(occurrence.id(), current));
                continue;
            }
            Long baseline = row == null ? null : row.getClassroomId();
            boolean changed = current != null && !current.equals(baseline);
            if (changed && !occurrence.date().isAfter(today.plusDays(WINDOW_DAYS))) {
                changes.add(new Change(occurrence, baseline, current));
            } else if (row == null) {
                baselines.add(row(occurrence.id(), null));
            }
        }
        notifiedRepository.saveAll(baselines);
        if (changes.isEmpty()) {
            log.info("Chequeo de autogestión: sin cambios de aula para los próximos {} días", WINDOW_DAYS);
            return;
        }

        messageService.send(new SendCommissionMessageRequest(
                target.publicLegajo(), target.publicUserType(), today.plusDays(target.untilDays()),
                commission.academicPeriod().year(), target.specialty(), target.subject(), commission.courseCode(),
                AllocationChangeNote.compose(lines(changes))));
        notifiedRepository.saveAll(changes.stream()
                .map(change -> row(change.occurrence().id(), change.toClassroom()))
                .toList());
    }

    private Map<Long, Long> currentRooms(List<OccurrenceResponseDto> occurrences) {
        Map<Long, Long> rooms = new HashMap<>();
        for (AllocationResponseDto allocation : allocationService
                .findByOccurrenceIds(occurrences.stream().map(OccurrenceResponseDto::id).toList())) {
            rooms.put(allocation.occurrence().id(), allocation.classroom().id());
        }
        return rooms;
    }

    private List<AllocationChangeNote.Line> lines(List<Change> changes) {
        Set<Long> classroomIds = new HashSet<>();
        changes.forEach(change -> {
            if (change.fromClassroom() != null) classroomIds.add(change.fromClassroom());
            classroomIds.add(change.toClassroom());
        });
        Map<Long, Integer> roomNumbers = classroomService.findByIdsIncludingDeactivated(classroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, ClassroomResponseDto::roomNumber));
        return changes.stream()
                .map(change -> new AllocationChangeNote.Line(change.occurrence().date(),
                        change.occurrence().startTime(), roomNumbers.get(change.fromClassroom()),
                        roomNumbers.get(change.toClassroom())))
                .toList();
    }

    private static AutogestionNotifiedRoom row(Long occurrenceId, Long classroomId) {
        return new AutogestionNotifiedRoom(occurrenceId, classroomId, Instant.now());
    }

    private record Change(OccurrenceResponseDto occurrence, Long fromClassroom, Long toClassroom) {
    }
}
