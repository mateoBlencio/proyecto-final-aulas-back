package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamOffScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PartialExamOffScheduleHandler extends AbstractRoomRequestHandler {

    public PartialExamOffScheduleHandler(AcademicReferenceValidator academicReference,
                                         ClassroomReferenceValidator classroomReference,
                                         ClassScheduleService classSchedule) {
        super(academicReference, classroomReference, classSchedule);
    }

    @Override
    public RoomRequestType type() {
        return RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE;
    }

    @Override
    protected void validateItems(CreateRoomRequestDto dto) {
        List<FreeFormItemDto> items = ((CreatePartialExamOffScheduleDto) dto).items();
        for (FreeFormItemDto item : items) {
            if (item.commissionId() == null) {
                throw new InvalidRoomRequestException("Cada pedido de parcial fuera de horario requiere una comisión.");
            }
            ItemConsistency.requireExamUsersConsistent(true, item);
        }
        ItemConsistency.requireDistinct(items.stream().map(FreeFormItemDto::commissionId).toList(), "una comisión");
    }

    @Override
    protected void validateReferences(CreateRoomRequestDto dto) {
        academicReference.requireSubject(dto.subjectId());
        ((CreatePartialExamOffScheduleDto) dto).items().stream()
                .map(FreeFormItemDto::commissionId)
                .distinct()
                .forEach(commissionId ->
                        academicReference.requireCommissionOfSubject(dto.subjectId(), commissionId));
    }

    @Override
    protected RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto) {
        FreeFormItemDto freeForm = (FreeFormItemDto) item;
        return freeFormItem(freeForm, freeForm.commissionId());
    }
}
