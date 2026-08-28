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

@Component
@RequiredArgsConstructor
public class RoomRequestValidator {

    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final ClassroomService classroomService;


    public void validateForCreation(CreateRoomRequestDto dto) {
        validateAcademicReference(dto);
        validateScheduleAndEnrollment(dto);
        validateExamUsers(dto);
        validateOtherHasObservations(dto);

        validateReferencesExist(dto);
        validateClassroomsExist(dto);
    }

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

    private void validateScheduleAndEnrollment(CreateRoomRequestDto dto) {
        RoomRequestType type = dto.type();
        if (!type.requiresScheduleAndEnrollment()) {
            return;
        }
        for (CreateRoomRequestItemDto item : dto.items()) {
            if (item.startTime() == null || item.endTime() == null) {
                throw new InvalidRoomRequestException(
                        "startTime y endTime son obligatorios en cada pedido para solicitudes de tipo " + type + ".");
            }
            if (item.enrolled() == null) {
                throw new InvalidRoomRequestException(
                        "enrolled es obligatorio en cada pedido para solicitudes de tipo " + type + ".");
            }
        }
    }

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

    private void validateClassroomsExist(CreateRoomRequestDto dto) {
        Set<Long> classroomIds = new LinkedHashSet<>();
        for (CreateRoomRequestItemDto item : dto.items()) {
            if (item.currentClassroomId() != null) {
                classroomIds.add(item.currentClassroomId());
            }
            classroomIds.addAll(item.preferredClassroomIds());
        }
        if (classroomIds.isEmpty()) {
            return;
        }

        Map<Long, ClassroomResponseDto> classroomsById =
                Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);
        for (Long classroomId : classroomIds) {
            if (!classroomsById.containsKey(classroomId)) {
                throw ResourceNotFoundException.of("Classroom", classroomId);
            }
        }
    }

    /**
     * Si-y-sólo-si: parcial/final con computadoras exige {@code true}/{@code false}
     * explícito (null = formulario incompleto); cualquier otro caso exige null.
     */
    private void validateExamUsers(CreateRoomRequestDto dto) {
        boolean examType = dto.type().isExam();
        for (CreateRoomRequestItemDto item : dto.items()) {
            boolean applies = examType && Boolean.TRUE.equals(item.requiresComputers());
            if (applies && item.requiresExamUsers() == null) {
                throw new InvalidRoomRequestException(
                        "requiresExamUsers es obligatorio en un pedido de parcial o final "
                                + "que requiere computadoras.");
            }
            if (!applies && item.requiresExamUsers() != null) {
                throw new InvalidRoomRequestException(
                        "requiresExamUsers solo puede indicarse en un pedido de parcial o final "
                                + "que además requiera computadoras.");
            }
        }
    }

    private void validateOtherHasObservations(CreateRoomRequestDto dto) {
        if (dto.type() != RoomRequestType.OTHER) {
            return;
        }
        for (CreateRoomRequestItemDto item : dto.items()) {
            if (item.observations() == null || item.observations().isBlank()) {
                throw new InvalidRoomRequestException(
                        "observations es obligatorio en cada pedido para solicitudes de tipo OTHER.");
            }
        }
    }

    public void validateTransition(RoomRequestStatus current, RoomRequestStatus target) {
        if (!current.allows(target)) {
            throw new InvalidRoomRequestTransitionException(current, target);
        }
    }
}
