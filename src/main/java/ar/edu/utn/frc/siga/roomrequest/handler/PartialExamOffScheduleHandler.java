package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamOffScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class PartialExamOffScheduleHandler extends AbstractRoomRequestHandler {

    private final RoomRequestItemRepository itemRepository;

    public PartialExamOffScheduleHandler(AcademicReferenceValidator academicReference,
                                         ClassroomReferenceValidator classroomReference,
                                         ClassScheduleService classSchedule,
                                         RoomRequestItemRepository itemRepository) {
        super(academicReference, classroomReference, classSchedule);
        this.itemRepository = itemRepository;
    }

    @Override
    public RoomRequestType type() {
        return RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE;
    }

    @Override
    protected void validateItems(CreateRoomRequestDto dto) {
        List<FreeFormItemDto> items = ((CreatePartialExamOffScheduleDto) dto).items();
        for (FreeFormItemDto item : items) {
            ItemConsistency.requireExamUsersConsistent(true, item);
            ItemConsistency.requireExamAdvanceNotice(item.date(), item.startTime());
        }
        ItemConsistency.requireNoCommissionOverlap(items);
    }

    @Override
    protected void validateReferences(CreateRoomRequestDto dto) {
        academicReference.requireSubject(dto.subjectId());
        ((CreatePartialExamOffScheduleDto) dto).items().stream()
                .map(FreeFormItemDto::commissionId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(commissionId ->
                        academicReference.requireCommissionOfSubject(dto.subjectId(), commissionId));
        requireNoOverlapWithExistingRequests(((CreatePartialExamOffScheduleDto) dto).items());
    }

    private void requireNoOverlapWithExistingRequests(List<FreeFormItemDto> items) {
        List<LocalDate> dates = items.stream().map(FreeFormItemDto::date).distinct().toList();
        Map<LocalDate, List<RoomRequestItem>> existingByDate = itemRepository
                .findActiveOffScheduleItemsByDateIn(dates).stream()
                .collect(Collectors.groupingBy(RoomRequestItem::getDate));

        for (FreeFormItemDto item : items) {
            boolean overlaps = existingByDate.getOrDefault(item.date(), List.of()).stream()
                    .anyMatch(existing -> ItemConsistency.commissionScheduleOverlap(
                            item.commissionId(), item.date(), item.startTime(), item.endTime(),
                            existing.getCommissionId(), existing.getDate(), existing.getStartTime(), existing.endTime()));
            if (overlaps) {
                throw new InvalidRoomRequestException(ItemConsistency.COMMISSION_OVERLAP_MESSAGE);
            }
        }
    }

    @Override
    protected RoomRequestItem buildItem(CreateRoomRequestItemDto item, CreateRoomRequestDto dto) {
        FreeFormItemDto freeForm = (FreeFormItemDto) item;
        return freeFormItem(freeForm, freeForm.commissionId());
    }
}
