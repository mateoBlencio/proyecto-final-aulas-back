package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.CursadoScheduleService;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Template method: {@link #validate} y {@link #assemble} fijan el orden (ítems → referencia
 * académica → aulas; cabecera → ítems → preferencias); cada handler concreto sólo completa los hooks.
 */
@RequiredArgsConstructor
public abstract class AbstractRoomRequestHandler implements RoomRequestTypeHandler {

    protected final AcademicReferenceValidator academicReference;
    protected final ClassroomReferenceValidator classroomReference;
    protected final CursadoScheduleService cursadoSchedule;

    @Override
    public final void validate(CreateRoomRequestDto dto) {
        validateItems(dto);
        validateReferences(dto);
        classroomReference.requireExist(allPreferredClassroomIds(dto));
    }

    @Override
    public final RoomRequest assemble(CreateRoomRequestDto dto) {
        RoomRequest request = RoomRequest.builder()
                .type(dto.type())
                .scope(dto.requester().scope())
                .teacherName(dto.requester().teacherName())
                .teacherEmail(dto.requester().teacherEmail())
                .teacherPhone(dto.requester().teacherPhone())
                .subjectId(dto.subjectId())
                .build();
        for (CreateRoomRequestItemDto item : dto.items()) {
            RoomRequestItem entity = buildItem(item, dto);
            request.addItem(entity);
            entity.addPreferences(item.preferredClassroomIds());
        }
        return request;
    }

    /** Presencia/ausencia de campos por tipo y consistencia entre ítems (datos del propio pedido). */
    protected abstract void validateItems(CreateRoomRequestDto dto);

    /** Referencias cruzadas: materia y comisión ({@code academic}) y bloqueo de calendario ({@code events}). */
    protected abstract void validateReferences(CreateRoomRequestDto dto);

    /** Arma la entidad de un ítem, derivando día/horario del cursado cuando corresponde. */
    protected abstract RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto);

    /** Ítem de un tipo con fecha y franja horaria cargadas por el docente (parcial fuera de horario, final, conferencia, otro). */
    protected RoomRequestItem freeFormItem(FreeFormItemDto item, Long commissionId) {
        return baseItem(item)
                .commissionId(commissionId)
                .date(item.date())
                .startTime(item.startTime())
                .duration(item.duration())
                .estimated(item.estimated())
                .build();
    }

    /** Campos comunes de un ítem; el handler completa fecha/día/horario/estimado. */
    protected RoomRequestItem.RoomRequestItemBuilder baseItem(CreateRoomRequestItemDto item) {
        return RoomRequestItem.builder()
                .classroomCount(item.classroomCount() == null ? 1 : item.classroomCount())
                .requiresProjector(Boolean.TRUE.equals(item.requiresProjector()))
                .requiresComputers(Boolean.TRUE.equals(item.requiresComputers()))
                .computerCount(item.computerCount())
                .requiresExamUsers(item.requiresExamUsers())
                .requiredSoftware(item.requiredSoftware())
                .observations(item.observations());
    }

    private List<Long> allPreferredClassroomIds(CreateRoomRequestDto dto) {
        return dto.items().stream()
                .flatMap(item -> item.preferredClassroomIds().stream())
                .toList();
    }
}
