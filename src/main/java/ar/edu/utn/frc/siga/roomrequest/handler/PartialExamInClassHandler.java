package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamInClassDto;
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
public class PartialExamInClassHandler extends AbstractRoomRequestHandler {

    public PartialExamInClassHandler(AcademicReferenceValidator academicReference,
                                     ClassroomReferenceValidator classroomReference,
                                     ClassScheduleService classSchedule) {
        super(academicReference, classroomReference, classSchedule);
    }

    @Override
    public RoomRequestType type() {
        return RoomRequestType.PARTIAL_EXAM_IN_CLASS;
    }

    @Override
    protected void validateItems(CreateRoomRequestDto dto) {
        List<ScheduledItemDto> items = ((CreatePartialExamInClassDto) dto).items();
        for (ScheduledItemDto item : items) {
            if (item.dayOfWeek() == null) {
                throw new InvalidRoomRequestException("Cada pedido de parcial en horario de clases requiere un día de dictado.");
            }
            if (item.date() != null) {
                throw new InvalidRoomRequestException("El parcial en horario de clases se ata a un día de dictado, no a una fecha.");
            }
            if (item.estimated() == null) {
                throw new InvalidRoomRequestException("Cada pedido de parcial requiere la cantidad estimada de asistentes.");
            }
            ItemConsistency.requireExamUsersConsistent(true, item);
        }
        ItemConsistency.requireDistinct(items.stream().map(ScheduledItemDto::dayOfWeek).toList(), "un día de dictado");
    }

    @Override
    protected void validateReferences(CreateRoomRequestDto dto) {
        academicReference.requireSubject(dto.subjectId());
        academicReference.requireCommissionOfSubject(dto.subjectId(), dto.commissionId());
        for (ScheduledItemDto item : ((CreatePartialExamInClassDto) dto).items()) {
            classSchedule.requireClassDay(dto.subjectId(), dto.commissionId(), item.dayOfWeek());
        }
    }

    @Override
    protected RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto) {
        ClassSlot slot = classSchedule.requireClassDay(dto.subjectId(), dto.commissionId(), item.dayOfWeek());
        return baseItem(item)
                .commissionId(dto.commissionId())
                .dayOfWeek(item.dayOfWeek())
                .startTime(slot.startTime())
                .duration(slot.duration())
                .estimated(item.estimated())
                .sourceRecurringEventId(slot.recurringEventId())
                .build();
    }
}
