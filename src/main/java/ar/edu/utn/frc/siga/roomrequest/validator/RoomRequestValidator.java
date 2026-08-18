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

    /**
     * Valida la solicitud completa antes de persistirla.
     *
     * <p>Primero todo lo que se resuelve con los datos del propio DTO y después
     * lo que necesita ir a la base: así una solicitud mal armada se rechaza sin
     * gastar una sola query. El orden es parte del contrato, no un detalle —
     * hay tests que verifican que un rechazo temprano no toca ningún módulo.
     */
    public void validateForCreation(CreateRoomRequestDto dto) {
        validateAcademicReference(dto);
        validateScheduleAndEnrollment(dto);
        validateExamUsers(dto);
        validateOtherHasObservations(dto);

        validateReferencesExist(dto);
        validateClassroomsExist(dto);
    }

    /**
     * Una charla, conferencia u otro tipo sin definir no está atada a una
     * materia; el resto de los tipos sí. Mismo criterio que
     * {@code EventScheduleValidator} en events.
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

    /**
     * {@code startTime}/{@code endTime}/{@code enrolled} son obligatorios salvo
     * en {@code ONE_TIME_ROOM_CHANGE}/{@code REGULAR_ROOM_CHANGE}, donde esos
     * datos ya salen de la comisión y su ocurrencia — pedirlos de nuevo en el
     * formulario sería redundante y una fuente más de inconsistencia. Bean
     * Validation no puede expresar "obligatorio según el tipo", así que
     * {@code CreateRoomRequestItemDto} los declara opcionales y la obligatoriedad
     * condicional se resuelve acá, igual que {@link #validateAcademicReference}.
     */
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
            classroomIds.addAll(item.preferredClassroomIds());
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

    /**
     * {@code requiresExamUsers} son usuarios de examen a nivel de la computadora
     * del aula, así que la pregunta sólo existe para un parcial o final que
     * además pide computadoras. La regla es un si y sólo si:
     *
     * <ul>
     *   <li>parcial o final <b>con</b> computadoras: el docente tiene que
     *       contestar, {@code true} o {@code false}. Un null ahí es el
     *       formulario incompleto, no un "no".</li>
     *   <li>cualquier otro caso: tiene que venir en null, porque la pregunta ni
     *       siquiera se le mostró.</li>
     * </ul>
     *
     * <p>Por eso {@code false} no es equivalente a null: uno significa "le
     * preguntamos y dijo que no" y el otro "no aplica". El valor se guarda como
     * {@code Boolean} nullable justamente para poder distinguirlos.
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

    /**
     * {@code OTHER} no tiene reglas de negocio propias todavía y no exige
     * referencia académica, así que sin este chequeo un pedido de ese tipo
     * podría llegar sin absolutamente ningún dato que diga de qué se trata.
     * {@code observations} es el único campo libre que ya existe en el item;
     * reusarlo evita otra columna/migración para algo que todavía no sabemos
     * cómo va a usarse en la práctica.
     */
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
