package ar.edu.utn.frc.siga.roomrequest.direct;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventDraftDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassSlot;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class DirectEventDraftValidator {

    public record ResolvedSchedule(LocalTime startTime, Duration duration) {
        public LocalTime endTime() {
            return startTime.plus(duration);
        }
    }

    private final UserService userService;
    private final AcademicReferenceValidator academicReferences;
    private final ClassScheduleService classSchedule;

    public ResolvedSchedule validate(DirectEventDraftDto draft, String actor) {
        requireSupportedType(draft.type());
        requireComputersForAuxiliar(draft, actor);
        validateAcademicReferences(draft);
        if (draft.type() == RoomRequestType.OTHER
                && (draft.description() == null || draft.description().isBlank())) {
            throw new InvalidRoomRequestException("description es obligatorio para eventos de tipo OTHER.");
        }
        ResolvedSchedule schedule = resolveSchedule(draft);
        ItemConsistency.requireNotPast(draft.date(), schedule.startTime());
        requireExamUsersConsistent(draft);
        return schedule;
    }

    private void requireSupportedType(RoomRequestType type) {
        if (type == RoomRequestType.ONE_TIME_ROOM_CHANGE || type == RoomRequestType.REGULAR_ROOM_CHANGE) {
            throw new InvalidRoomRequestException("El tipo " + type + " no crea un evento con aula asignada.");
        }
    }

    private void requireComputersForAuxiliar(DirectEventDraftDto draft, String actor) {
        if (!Boolean.TRUE.equals(draft.requiresComputers()) && !userService.hasRole(actor, SystemRole.SUBSECRETARIA)) {
            throw new InvalidRoomRequestException("El auxiliar solo puede asignar aulas con computadoras.");
        }
    }

    private void validateAcademicReferences(DirectEventDraftDto draft) {
        switch (draft.type()) {
            case PARTIAL_EXAM_IN_CLASS -> {
                academicReferences.requireSubject(draft.subjectId());
                academicReferences.requireCommissionOfSubject(draft.subjectId(), draft.commissionId());
            }
            case PARTIAL_EXAM_OFF_SCHEDULE -> {
                academicReferences.requireSubject(draft.subjectId());
                if (draft.commissionId() != null) {
                    academicReferences.requireCommissionOfSubject(draft.subjectId(), draft.commissionId());
                }
            }
            case FINAL_EXAM -> {
                academicReferences.requireSubject(draft.subjectId());
                requireNoCommission(draft, "Un examen final no lleva comisión.");
            }
            default -> {
                academicReferences.validateOptionalSubject(draft.subjectId());
                requireNoCommission(draft, "Este tipo de evento no lleva comisión.");
            }
        }
    }

    private static void requireNoCommission(DirectEventDraftDto draft, String message) {
        if (draft.commissionId() != null) {
            throw new InvalidRoomRequestException(message);
        }
    }

    private ResolvedSchedule resolveSchedule(DirectEventDraftDto draft) {
        if (draft.type() == RoomRequestType.PARTIAL_EXAM_IN_CLASS) {
            if (draft.startTime() != null || draft.endTime() != null) {
                throw new InvalidRoomRequestException(
                        "El parcial en horario de clases toma el horario de cursado: no enviar startTime ni endTime.");
            }
            ClassSlot slot = classSchedule.requireClassDate(draft.subjectId(), draft.commissionId(), draft.date());
            return new ResolvedSchedule(slot.startTime(), slot.duration());
        }
        if (draft.startTime() == null || draft.endTime() == null) {
            throw new InvalidRoomRequestException("startTime y endTime son obligatorios para este tipo de evento.");
        }
        if (!draft.endTime().isAfter(draft.startTime())) {
            throw new InvalidRoomRequestException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        return new ResolvedSchedule(draft.startTime(), Duration.between(draft.startTime(), draft.endTime()));
    }

    private static void requireExamUsersConsistent(DirectEventDraftDto draft) {
        boolean examType = draft.type() == RoomRequestType.PARTIAL_EXAM_IN_CLASS
                || draft.type() == RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE
                || draft.type() == RoomRequestType.FINAL_EXAM;
        boolean applies = examType && Boolean.TRUE.equals(draft.requiresComputers());
        if (applies && draft.requiresExamUsers() == null) {
            throw new InvalidRoomRequestException(
                    "requiresExamUsers es obligatorio en un examen que requiere computadoras.");
        }
        if (!applies && draft.requiresExamUsers() != null) {
            throw new InvalidRoomRequestException(
                    "requiresExamUsers solo puede indicarse en un examen que además requiera computadoras.");
        }
    }
}
