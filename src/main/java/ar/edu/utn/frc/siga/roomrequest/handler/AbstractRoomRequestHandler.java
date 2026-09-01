package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractRoomRequestHandler implements RoomRequestTypeHandler {

    protected final AcademicReferenceValidator academicReference;
    protected final ClassroomReferenceValidator classroomReference;
    protected final ClassScheduleService classSchedule;

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

    protected abstract void validateItems(CreateRoomRequestDto dto);

    protected abstract void validateReferences(CreateRoomRequestDto dto);

    protected abstract RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto);

    protected RoomRequestItem freeFormItem(FreeFormItemDto item, Long commissionId) {
        return baseItem(item)
                .commissionId(commissionId)
                .date(item.date())
                .startTime(item.startTime())
                .duration(item.duration())
                .estimated(item.estimated())
                .build();
    }

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
