package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de negocio que Bean Validation no puede expresar: obligatoriedad
 * condicional por tipo, existencia de las referencias a otros módulos y
 * legalidad de los cambios de estado.
 */
@Component
@RequiredArgsConstructor
public class RoomRequestValidator {

    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final ClassroomService classroomService;

    /** Valida la solicitud completa antes de persistirla. */
    public void validateForCreation(CreateRoomRequestDto dto) {
        validateAcademicReference(dto);
        validateReferencesExist(dto);
        validateClassroomsExist(dto);
    }

    /**
     * Una charla o conferencia no está atada a una materia; el resto de los
     * tipos sí. Mismo criterio que {@code EventScheduleValidator} en events.
     */
    private void validateAcademicReference(CreateRoomRequestDto dto) {
        RoomRequestType type = dto.type();
        if (!type.requiresAcademicReference()) {
            return;
        }
        if (dto.subjectId() == null) {
            throw new InvalidRoomRequestException(
                    "subjectId es obligatorio para solicitudes de tipo " + type + ".");
        }
        for (CreateRoomRequestItemDto item : dto.items()) {
            if (item.commissionId() == null) {
                throw new InvalidRoomRequestException(
                        "commissionId es obligatorio en cada pedido para solicitudes de tipo " + type + ".");
            }
        }
    }

    /** Materia y comisiones existen, y cada comisión pertenece a la materia. */
    private void validateReferencesExist(CreateRoomRequestDto dto) {
        if (dto.subjectId() != null) {
            subjectService.findById(dto.subjectId());
        }
        for (CreateRoomRequestItemDto item : dto.items()) {
            if (item.commissionId() == null) {
                continue;
            }
            commissionService.findById(item.commissionId());
            validateCommissionBelongsToSubject(dto.subjectId(), item.commissionId());
        }
    }

    private void validateCommissionBelongsToSubject(Long subjectId, Long commissionId) {
        if (subjectId == null) {
            return;
        }
        try {
            subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId);
        } catch (ResourceNotFoundException e) {
            throw new InvalidRoomRequestException(
                    "La comisión " + commissionId + " no pertenece a la materia " + subjectId + ".");
        }
    }

    /**
     * Las aulas referenciadas existen. No se valida disponibilidad ni capacidad:
     * son preferencias declaradas por el docente, no una asignación.
     */
    private void validateClassroomsExist(CreateRoomRequestDto dto) {
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (CreateRoomRequestItemDto item : dto.items()) {
            if (item.currentClassroomId() != null) {
                classroomIds.add(item.currentClassroomId());
            }
            if (item.preferredClassroomIds() != null) {
                classroomIds.addAll(item.preferredClassroomIds());
            }
        }
        if (classroomIds.isEmpty()) {
            return;
        }

        Map<Integer, ClassroomResponseDto> classroomsById =
                Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);
        for (Integer classroomId : classroomIds) {
            if (!classroomsById.containsKey(classroomId)) {
                throw ResourceNotFoundException.of("Classroom", classroomId);
            }
        }
    }

    public void validateTransition(RoomRequestStatus current, RoomRequestStatus target) {
        if (!current.allows(target)) {
            throw new InvalidRoomRequestTransitionException(current, target);
        }
    }
}
