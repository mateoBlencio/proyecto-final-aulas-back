package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateFinalExamDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.CursadoScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import org.springframework.stereotype.Component;

import java.util.List;

/** Final: por materia, sin comisión, un solo pedido. */
@Component
public class FinalExamHandler extends AbstractRoomRequestHandler {

    public FinalExamHandler(AcademicReferenceValidator academicReference,
                            ClassroomReferenceValidator classroomReference,
                            CursadoScheduleService cursadoSchedule) {
        super(academicReference, classroomReference, cursadoSchedule);
    }

    @Override
    public RoomRequestType type() {
        return RoomRequestType.FINAL_EXAM;
    }

    @Override
    protected void validateItems(CreateRoomRequestDto dto) {
        List<FreeFormItemDto> items = ((CreateFinalExamDto) dto).items();
        ItemConsistency.requireExactlyOne(items.size());
        for (FreeFormItemDto item : items) {
            if (item.commissionId() != null) {
                throw new InvalidRoomRequestException("El final se solicita por materia: los pedidos no llevan comisión.");
            }
            ItemConsistency.requireExamUsersConsistent(true, item);
        }
    }

    @Override
    protected void validateReferences(CreateRoomRequestDto dto) {
        academicReference.requireSubject(dto.subjectId());
    }

    @Override
    protected RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto) {
        return freeFormItem((FreeFormItemDto) item, null);
    }
}
