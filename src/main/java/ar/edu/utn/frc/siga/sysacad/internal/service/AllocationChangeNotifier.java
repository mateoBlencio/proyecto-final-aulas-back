package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"siga.sysacad.enabled", "siga.sysacad.autogestion.enabled"}, havingValue = "true")
class AllocationChangeNotifier {

    private final SysacadCommissionMessageService messageService;
    private final AcademicEventService academicEventService;
    private final ClassroomService classroomService;
    private final SysacadProperties properties;

    @ApplicationModuleListener
    void on(ClassroomMovements movements) {
        try {
            notifyTarget(movements);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar por autogestión el cambio de aula de la operación {}",
                    movements.operationId(), e);
        }
    }

    private void notifyTarget(ClassroomMovements movements) {
        Set<Long> eventIds = movements.changes().stream().map(Change::eventId).collect(Collectors.toSet());
        Map<Long, AcademicEventResponseDto> targetEvents = academicEventService.findByIds(eventIds).stream()
                .filter(this::isTarget)
                .collect(Collectors.toMap(AcademicEventResponseDto::id, Function.identity()));
        List<Change> changes = movements.changes().stream()
                .filter(change -> targetEvents.containsKey(change.eventId()))
                .toList();
        if (changes.isEmpty()) {
            log.debug("Operación {} sin cambios de la comisión habilitada para autogestión", movements.operationId());
            return;
        }
        log.info("Operación {}: {} cambios de aula de la comisión habilitada para autogestión",
                movements.operationId(), changes.size());

        Map<Long, Integer> roomNumbers = roomNumbers(changes);
        List<AllocationChangeNote.Line> lines = changes.stream()
                .map(change -> new AllocationChangeNote.Line(change.date(), change.startTime(),
                        roomNumbers.get(change.fromClassroomId()), roomNumbers.get(change.toClassroomId())))
                .toList();
        CommissionResponseDto commission = commissionOf(targetEvents.values().iterator().next());
        SysacadProperties.Autogestion autogestion = properties.getAutogestion();

        messageService.send(new SendCommissionMessageRequest(
                autogestion.publicLegajo(),
                autogestion.publicUserType(),
                LocalDate.now().plusDays(autogestion.untilDays()),
                commission.academicPeriod().year(),
                autogestion.specialty(),
                autogestion.subject(),
                commission.courseCode(),
                AllocationChangeNote.compose(lines)));
    }

    private Map<Long, Integer> roomNumbers(List<Change> changes) {
        Set<Long> classroomIds = new HashSet<>();
        changes.forEach(change -> {
            if (change.fromClassroomId() != null) classroomIds.add(change.fromClassroomId());
            if (change.toClassroomId() != null) classroomIds.add(change.toClassroomId());
        });
        return classroomService.findByIdsIncludingDeactivated(classroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, ClassroomResponseDto::roomNumber));
    }

    private boolean isTarget(AcademicEventResponseDto event) {
        CommissionResponseDto commission = commissionOf(event);
        SubjectResponseDto subject = event.subject();
        return commission != null && subject != null
                && messageService.isTarget(commission.courseCode(),
                        subject.studyPlan().specialty().specialtyCode(), subject.code());
    }

    private static CommissionResponseDto commissionOf(AcademicEventResponseDto event) {
        return switch (event) {
            case RecurringEventResponseDto recurring -> recurring.commission();
            case UniqueEventResponseDto unique -> unique.commission();
        };
    }
}
