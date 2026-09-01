package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOneTimeRoomChangeDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.ScheduledItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassSlot;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OneTimeRoomChangeHandler extends AbstractRoomRequestHandler {

    public OneTimeRoomChangeHandler(AcademicReferenceValidator academicReference,
                                    ClassroomReferenceValidator classroomReference,
                                    ClassScheduleService classSchedule) {
        super(academicReference, classroomReference, classSchedule);
    }

    @Override
    public RoomRequestType type() {
        return RoomRequestType.ONE_TIME_ROOM_CHANGE;
    }

    @Override
    protected void validateItems(CreateRoomRequestDto dto) {
        List<ScheduledItemDto> items = ((CreateOneTimeRoomChangeDto) dto).items();
        for (ScheduledItemDto item : items) {
            if (item.date() == null) {
                throw new InvalidRoomRequestException("Cada pedido de cambio de aula por única vez requiere una fecha.");
            }
            if (item.dayOfWeek() != null) {
                throw new InvalidRoomRequestException(
                        "El cambio de aula por única vez se ata a una fecha, no a un día de dictado.");
            }
            if (item.estimated() != null) {
                throw new InvalidRoomRequestException("El cambio de aula no lleva cantidad estimada de asistentes.");
            }
            ItemConsistency.requireExamUsersConsistent(false, item);
        }
        ItemConsistency.requireDistinct(items.stream().map(ScheduledItemDto::date).toList(), "una fecha");
    }

    @Override
    protected void validateReferences(CreateRoomRequestDto dto) {
        academicReference.requireSubject(dto.subjectId());
        academicReference.requireCommissionOfSubject(dto.subjectId(), dto.commissionId());
        for (ScheduledItemDto item : ((CreateOneTimeRoomChangeDto) dto).items()) {
            classSchedule.requireClassDate(dto.subjectId(), dto.commissionId(), item.date());
        }
    }

    @Override
    protected RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto) {
        ClassSlot slot = classSchedule.requireClassDate(dto.subjectId(), dto.commissionId(), item.date());
        return baseItem(item)
                .commissionId(dto.commissionId())
                .date(item.date())
                .startTime(slot.startTime())
                .duration(slot.duration())
                .sourceRecurringEventId(slot.recurringEventId())
                .build();
    }
}
