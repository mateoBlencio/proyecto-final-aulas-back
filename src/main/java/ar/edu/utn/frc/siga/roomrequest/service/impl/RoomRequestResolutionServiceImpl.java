package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ReversionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.event.RoomRequestItemAccepted;
import ar.edu.utn.frc.siga.roomrequest.event.RoomRequestItemReopened;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.exception.PartialAssignmentReasonRequiredException;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestAlreadyNotifiedException;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestOriginalOccupiedException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestNotificationModel;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl.Action;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestResolutionServiceImpl implements RoomRequestResolutionService {

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestTransitionValidator transitionValidator;
    private final RoomRequestAccessControl accessControl;
    private final AllocationService allocationService;
    private final OccurrenceService occurrenceService;
    private final BuildingService buildingService;
    private final UserService userService;
    private final NotificationSender notificationSender;
    private final RoomRequestNotificationModel notificationModel;
    private final RoomRequestComposer composer;
    private final RoomRequestOccurrenceResolver occurrenceResolver;
    private final RoomRequestCandidateResolver candidateResolver;
    private final RoomRequestAttendees attendees;
    private final ClassroomService classroomService;
    private final AcceptanceDeadlineCalculator deadlineCalculator;
    private final ApplicationEventPublisher eventPublisher;
    private final RoomRequestOriginalRestorer originalRestorer;

    @Override
    @Transactional
    @AuditOperation("Asignación de aula a solicitud")
    public RoomRequestItemResponseDto assign(Long itemId, List<Long> classroomIds, String reason, String actor) {
        return assign(itemId, classroomIds, reason, actor, items -> AllocationCommand.manual(items, reason));
    }

    @Override
    @Transactional
    @AuditOperation("Asignación automática de aula a solicitud")
    public RoomRequestItemResponseDto assignAutomatic(Long itemId, List<Long> classroomIds, String reason,
                                                      String actor) {
        return assign(itemId, classroomIds, reason, actor, items -> AllocationCommand.automatic(items, reason));
    }

    private RoomRequestItemResponseDto assign(Long itemId, List<Long> classroomIds, String reason, String actor,
                                              Function<List<AllocationItem>, AllocationCommand> commandFactory) {
        log.debug("Asignando aula(s) a pedido: itemId={}, classroomIds={}", itemId, classroomIds);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        if (item.getStatus() == RoomRequestStatus.NOTIFIED || item.getStatus() == RoomRequestStatus.RESOLVED) {
            throw new RoomRequestAlreadyNotifiedException(itemId);
        }
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.IN_EVALUATION);
        accessControl.authorize(item, actor, Action.ASSIGN);

        List<Long> ids = classroomIds == null ? List.of() : classroomIds;
        if (ids.isEmpty()) {
            throw new InvalidRoomRequestException("Hay que asignar al menos un aula.");
        }
        ItemConsistency.requireDistinct(ids, "un aula");
        boolean noReason = reason == null || reason.isBlank();
        List<ClassroomResponseDto> classrooms = classroomService.findByIds(ids);

        requireDerivedBuilding(item, classrooms);
        if (Boolean.TRUE.equals(item.getRequiresComputers())) {
            int assignedPcs = candidateResolver.computersByClassroom(ids).values().stream()
                    .mapToInt(Integer::intValue).sum();
            if (item.getComputerCount() != null && assignedPcs < item.getComputerCount() && noReason) {
                throw new PartialAssignmentReasonRequiredException("El motivo es obligatorio: las aulas elegidas suman "
                        + assignedPcs + " de las " + item.getComputerCount() + " computadoras pedidas.");
            }
        } else {
            if (ids.size() > item.getClassroomCount() && noReason) {
                throw new PartialAssignmentReasonRequiredException("El motivo es obligatorio: se asignan "
                        + ids.size() + " aulas y el pedido pide " + item.getClassroomCount() + ".");
            }
            if (ids.size() < item.getClassroomCount() && noReason) {
                throw new PartialAssignmentReasonRequiredException();
            }
        }
        if (noReason) {
            int capacity = classrooms.stream().mapToInt(ClassroomResponseDto::capacity).sum();
            attendees.of(item).filter(n -> capacity < n).ifPresent(n -> {
                throw new PartialAssignmentReasonRequiredException(
                        "El motivo es obligatorio: las aulas elegidas no tienen lugar para los " + n + " asistentes.");
            });
        }

        boolean reassigning = !item.getAllocations().isEmpty();
        List<Long> retiredOccurrenceIds = reassigning ? occurrenceResolver.releaseOldMirrors(item) : List.of();

        List<List<Long>> occurrencesBySlot = occurrenceResolver.resolveOccurrencesBySlot(item, ids.size(), reassigning);

        RoomRequestType type = item.getRequest().getType();
        Map<Long, RoomRequestItemAllocation.Original> originalByOccurrence = Map.of();
        if (RoomRequestOriginalRestorer.hasOriginalClassroom(type)) {
            // Se lee antes del reallocate: el upsert pisa el origen (SYSACAD pasa a MANUAL).
            originalByOccurrence = originalRestorer.captureOriginals(occurrencesBySlot.getFirst());
            RoomRequestItemAllocation.Original first = originalByOccurrence.get(occurrencesBySlot.get(0).getFirst());
            if (first != null) {
                item.rememberPreviousClassroom(first.classroomId());
            }
        }

        // Reactivar explícitamente las fechas liberadas (D7): sin esto reallocate las saltea y el
        // pedido quedaría registrando ocurrencias sin asignación. Solo las principales (slot 1): los
        // espejos se crean nuevos en NEEDS_ROOM, y reactivar uno viejo reviviría una lápida.
        occurrenceService.requestRoom(occurrencesBySlot.getFirst());

        List<AllocationItem> allocationItems = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            allocationItems.add(new AllocationItem(
                    new AllocationTarget.Occurrences(occurrencesBySlot.get(i)), ids.get(i)));
        }
        allocationService.reallocate(commandFactory.apply(allocationItems));

        item.assignClassrooms(ids, occurrencesBySlot, retiredOccurrenceIds, originalByOccurrence);
        item.decide(RoomRequestStatus.IN_EVALUATION, actor, reason, Instant.now());

        log.info("Pedido de aula asignado: itemId={}, aulas={}, ocurrencias={}",
                itemId, ids.size(), occurrencesBySlot.stream().mapToInt(List::size).sum());
        return composer.composeItem(item);
    }

    private void requireDerivedBuilding(RoomRequestItem item, List<ClassroomResponseDto> classrooms) {
        Long derivedBuildingId = item.getDerivedBuildingId();
        if (derivedBuildingId != null && classrooms.stream().anyMatch(c -> !derivedBuildingId.equals(c.buildingId()))) {
            throw new InvalidRoomRequestException("Las aulas tienen que ser del edificio al que se derivó el pedido.");
        }
    }

    @Override
    @Transactional
    @AuditOperation("Cancelación de solicitud de aula")
    public RoomRequestItemResponseDto cancel(Long itemId, String reason, String actor) {
        log.debug("Cancelando pedido de aula: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de cancelación es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        if (item.getStatus() == RoomRequestStatus.RESOLVED) {
            throw new RoomRequestAlreadyNotifiedException(itemId);
        }
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.CANCELLED);
        accessControl.authorize(item, actor, Action.CANCEL);

        String observation = "Solicitud de aula cancelada: " + reason;
        List<Long> deallocatedIds;
        if (RoomRequestOriginalRestorer.hasOriginalClassroom(item.getRequest().getType())) {
            occurrenceResolver.releaseOldMirrors(item);
            RoomRequestOriginalRestorer.Restoration restoration = originalRestorer.restore(item, observation, true);
            if (!restoration.occupied().isEmpty()) {
                throw new RoomRequestOriginalOccupiedException(itemId, originalRestorer.toConflicts(restoration));
            }
            item.moveToOriginal(restoration.restored());
            deallocatedIds = restoration.toDeallocate();
        } else {
            occurrenceResolver.releaseOldMirrors(item);
            deallocatedIds = occurrenceResolver.onlyFuture(item, item.getAllocations().stream()
                    .filter(allocation -> allocation.getPosition() == 1)
                    .map(RoomRequestItemAllocation::getOccurrenceId)
                    .toList());
        }
        if (!deallocatedIds.isEmpty()) {
            allocationService.deallocate(new DeallocationCommand(
                    List.of(new AllocationTarget.Occurrences(deallocatedIds)), observation));
        }

        item.decide(RoomRequestStatus.CANCELLED, actor, reason, Instant.now());

        log.info("Pedido de aula cancelado: itemId={}, aulasLiberadas={}", itemId, deallocatedIds.size());
        return composer.composeItem(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllowedClassroomDto> findAllowedClassrooms(Long itemId, String actor) {
        authorizeRead(itemId, actor);
        return candidateResolver.findAllowedClassrooms(itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateBuildingDto> findCandidateBuildings(Long itemId, String actor) {
        authorizeRead(itemId, actor);
        return candidateResolver.findCandidateBuildings(itemId);
    }

    @Override
    @Transactional
    @AuditOperation("Derivación de solicitud de aula a otro edificio")
    public RoomRequestItemResponseDto derive(Long itemId, Long buildingId, String actor) {
        log.debug("Derivando pedido de aula: itemId={}, buildingId={}", itemId, buildingId);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.DERIVED_TO_BUILDING);
        accessControl.authorize(item, actor, Action.DERIVE);

        BuildingResponseDto building = buildingService.findById(buildingId);
        if (!Boolean.TRUE.equals(building.active())) {
            throw new InvalidRoomRequestException("El edificio no está activo.");
        }
        if (userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, buildingId).isEmpty()) {
            throw new InvalidRoomRequestException("El edificio no tiene un auxiliar áulico asignado.");
        }

        item.deriveTo(buildingId, actor, Instant.now());

        log.info("Pedido de aula derivado: itemId={}, buildingId={}", itemId, buildingId);
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    @AuditOperation("Devolución de solicitud de aula")
    public RoomRequestItemResponseDto returnItem(Long itemId, String reason, String actor) {
        log.debug("Devolviendo pedido de aula desde su edificio: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de devolución es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.NEW);
        accessControl.authorize(item, actor, Action.RETURN);

        item.returnFromBuilding(reason);

        log.info("Pedido de aula devuelto: itemId={}", itemId);
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    @AuditOperation("Notificación de solicitud de aula")
    public RoomRequestItemResponseDto notify(Long itemId, String actor) {
        log.debug("Notificando pedido de aula: itemId={}", itemId);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        accessControl.authorizeRead(item, actor);
        if (item.getStatus() == RoomRequestStatus.NOTIFIED || item.getStatus() == RoomRequestStatus.RESOLVED) {
            log.debug("Pedido ya notificado, devuelve el estado actual sin resellar: itemId={}", itemId);
            return composer.composeItem(item);
        }

        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.NOTIFIED);
        accessControl.authorize(item, actor, Action.NOTIFY);
        if (item.getAllocations().isEmpty()) {
            throw new InvalidRoomRequestException("El pedido no tiene ninguna aula asignada.");
        }

        Instant now = Instant.now();
        item.notifyTeacher(actor, now, deadlineCalculator.calculate(now, eventStart(item)));

        RoomRequestItemDetailDto detail = composer.composeDetail(item);
        try {
            notificationSender.send(new NotificationRequest(
                    NotificationTemplate.ROOM_REQUEST_RESOLVED,
                    List.of(new NotificationRecipient(detail.request().teacherName(), detail.request().teacherEmail())),
                    notificationModel.build(detail),
                    "room-request-item:" + itemId + ":NOTIFIED:" + item.getNotificationRound(),
                    replyToFor(item)));
        } catch (RuntimeException e) {
            log.error("No se pudo encolar la notificación de pedido resuelto: itemId={}", itemId, e);
        }

        log.info("Pedido de aula notificado: itemId={}, ronda={}", itemId, item.getNotificationRound());
        return detail.item();
    }

    @Override
    @Transactional
    @AuditOperation("Confirmación de aceptación de solicitud de aula")
    public RoomRequestItemResponseDto confirm(Long itemId, String actor) {
        log.debug("Confirmando aceptación de pedido de aula: itemId={}", itemId);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        accessControl.authorizeRead(item, actor);
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.RESOLVED);
        accessControl.authorize(item, actor, Action.CONFIRM);

        Instant now = Instant.now();
        item.accept(actor, now);
        eventPublisher.publishEvent(new RoomRequestItemAccepted(itemId, actor, now));

        log.info("Aceptación de pedido de aula confirmada: itemId={}", itemId);
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    @AuditOperation("Reapertura de solicitud de aula")
    public RoomRequestItemResponseDto reopen(Long itemId, String reason, String actor) {
        log.debug("Reabriendo pedido de aula: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de reapertura es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        accessControl.authorizeRead(item, actor);
        if (item.getStatus() != RoomRequestStatus.NOTIFIED && item.getStatus() != RoomRequestStatus.RESOLVED) {
            throw new InvalidRoomRequestTransitionException(item.getStatus(), RoomRequestStatus.IN_EVALUATION);
        }
        accessControl.authorize(item, actor, Action.REOPEN);
        if (item.getStatus() == RoomRequestStatus.RESOLVED && !hasFutureDates(item)) {
            throw new InvalidRoomRequestException("El pedido no se puede reabrir: ya no le quedan fechas futuras.");
        }

        RoomRequestStatus previous = item.getStatus();
        ReversionDto reversion = null;
        if (previous == RoomRequestStatus.NOTIFIED
                && RoomRequestOriginalRestorer.hasOriginalClassroom(item.getRequest().getType())) {
            RoomRequestOriginalRestorer.Restoration restoration = originalRestorer.restore(item,
                    "Reapertura de pedido de aula #" + itemId + ": vuelve al aula original", false);
            item.moveToOriginal(restoration.restored());
            item.moveMirrors(restoration.swappedMirrors());
            reversion = originalRestorer.toReversion(item, restoration);
        }
        Instant now = Instant.now();
        item.reopen(reason, actor, now);
        if (previous == RoomRequestStatus.NOTIFIED) {
            item.markReopenedFromNotified(LocalDate.now(AcceptanceDeadlineCalculator.ZONE));
        }
        eventPublisher.publishEvent(new RoomRequestItemReopened(itemId, previous, reason, actor, now));

        log.info("Pedido de aula reabierto: itemId={}, desde={}", itemId, previous);
        RoomRequestItemResponseDto response = composer.composeItem(item);
        return reversion == null ? response : RoomRequestItemResponseDto.withReversion(response, reversion);
    }

    private List<String> replyToFor(RoomRequestItem item) {
        if (item.getDerivedBuildingId() == null) {
            return List.of();
        }
        return userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, item.getDerivedBuildingId()).stream()
                .map(UserResponseDto::email)
                .toList();
    }

    private void authorizeRead(Long itemId, String actor) {
        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        accessControl.authorizeRead(item, actor);
    }

    private boolean hasFutureDates(RoomRequestItem item) {
        if (item.getRequest().getType() == RoomRequestType.REGULAR_ROOM_CHANGE) {
            return occurrenceResolver.nextClassDate(item).isPresent();
        }
        return item.getDate() != null && !item.getDate().isBefore(LocalDate.now(AcceptanceDeadlineCalculator.ZONE));
    }

    /** Inicio del evento en hora local; para un cambio regular, el de la próxima clase. Nulo si no hay. */
    private LocalDateTime eventStart(RoomRequestItem item) {
        if (item.getStartTime() == null) {
            return null;
        }
        LocalDate date = item.getRequest().getType() == RoomRequestType.REGULAR_ROOM_CHANGE
                ? occurrenceResolver.nextClassDate(item).orElse(null)
                : item.getDate();
        return date == null ? null : date.atTime(item.getStartTime());
    }
}
