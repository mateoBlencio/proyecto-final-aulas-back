package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateConferenceDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.CursadoScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import org.springframework.stereotype.Component;

/** Conferencia: materia y comisión opcionales; fecha y franja horaria libres. */
@Component
public class ConferenceHandler extends AbstractRoomRequestHandler {

    public ConferenceHandler(AcademicReferenceValidator academicReference,
                             ClassroomReferenceValidator classroomReference,
                             CursadoScheduleService cursadoSchedule) {
        super(academicReference, classroomReference, cursadoSchedule);
    }

    @Override
    public RoomRequestType type() {
        return RoomRequestType.CONFERENCE;
    }

    @Override
    protected void validateItems(CreateRoomRequestDto dto) {
        for (FreeFormItemDto item : ((CreateConferenceDto) dto).items()) {
            ItemConsistency.requireExamUsersConsistent(false, item);
        }
    }

    @Override
    protected void validateReferences(CreateRoomRequestDto dto) {
        academicReference.validateOptionalSubject(dto.subjectId());
        academicReference.validateOptionalCommission(dto.subjectId(), dto.commissionId());
    }

    @Override
    protected RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto) {
        return freeFormItem((FreeFormItemDto) item, dto.commissionId());
    }
}
