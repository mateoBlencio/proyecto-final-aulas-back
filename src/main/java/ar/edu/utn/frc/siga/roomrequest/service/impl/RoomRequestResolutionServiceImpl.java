package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestResolved;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Transiciones de estado de un ítem (assign/cancel/derive/return/notify). La resolución de qué ocurrencias u
 *  aulas/edificios son candidatos vive en {@link RoomRequestOccurrenceResolver} y {@link RoomRequestCandidateResolver}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestResolutionServiceImpl implements RoomRequestResolutionService {

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestTransitionValidator transitionValidator;
    private final AllocationService allocationService;
    private final BuildingService buildingService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final RoomRequestComposer composer;
    private final RoomRequestOccurrenceResolver occurrenceResolver;
    private final RoomRequestCandidateResolver candidateResolver;

    @Override
    @Transactional
    public RoomRequestItemResponseDto assign(Long itemId, List<Long> classroomIds, String reason, String actor) {
        log.debug("Asignando aula(s) a pedido: itemId={}, classroomIds={}", itemId, classroomIds);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.IN_EVALUATION);

        List<Long> ids = classroomIds == null ? List.of() : classroomIds;
        if (ids.isEmpty()) {
            throw new InvalidRoomRequestException("Hay que asignar al menos un aula.");
        }
        if (ids.size() > item.getClassroomCount()) {
            throw new InvalidRoomRequestException("No se pueden asignar más aulas de las pedidas.");
        }
        ItemConsistency.requireDistinct(ids, "un aula");
        if (ids.size() < item.getClassroomCount() && (reason == null || reason.isBlank())) {
            throw new InvalidRoomRequestException(
                    "El motivo es obligatorio al asignar menos aulas de las pedidas.");
        }

        boolean reassigning = !item.getAllocations().isEmpty();
        if (reassigning) {
            occurrenceResolver.releaseOldMirrors(item);
        }

        List<List<Long>> occurrencesBySlot = occurrenceResolver.resolveOccurrencesBySlot(item, ids.size(), reassigning);

        List<AllocationItem> allocationItems = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            allocationItems.add(new AllocationItem(
                    new AllocationTarget.Occurrences(occurrencesBySlot.get(i)), ids.get(i)));
        }
        allocationService.reallocate(AllocationCommand.manual(allocationItems, reason));

        item.assignClassrooms(ids, occurrencesBySlot);
        item.decide(RoomRequestStatus.IN_EVALUATION, actor, reason, LocalDateTime.now());

        log.info("Pedido de aula asignado: itemId={}, aulas={}, ocurrencias={}",
                itemId, ids.size(), occurrencesBySlot.stream().mapToInt(List::size).sum());
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto cancel(Long itemId, String reason, String actor) {
        log.debug("Cancelando pedido de aula: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de cancelación es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.CANCELLED);

        List<Long> occurrenceIds = item.getAllocations().stream()
                .map(RoomRequestItemAllocation::getOccurrenceId)
                .toList();
        if (!occurrenceIds.isEmpty()) {
            allocationService.deallocate(new DeallocationCommand(
                    List.of(new AllocationTarget.Occurrences(occurrenceIds)),
                    "Solicitud de aula cancelada: " + reason));
        }

        item.decide(RoomRequestStatus.CANCELLED, actor, reason, LocalDateTime.now());

        log.info("Pedido de aula cancelado: itemId={}, aulasLiberadas={}", itemId, occurrenceIds.size());
        return composer.composeItem(item);
    }

    @Override
    public List<AllowedClassroomDto> findAllowedClassrooms(Long itemId) {
        return candidateResolver.findAllowedClassrooms(itemId);
    }

    @Override
    public List<CandidateBuildingDto> findCandidateBuildings(Long itemId) {
        return candidateResolver.findCandidateBuildings(itemId);
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto derive(Long itemId, Long buildingId, String actor) {
        log.debug("Derivando pedido de aula: itemId={}, buildingId={}", itemId, buildingId);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.DERIVED_TO_BUILDING);

        BuildingResponseDto building = buildingService.findById(buildingId);
        if (!Boolean.TRUE.equals(building.active())) {
            throw new InvalidRoomRequestException("El edificio no está activo.");
        }
        if (userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, buildingId).isEmpty()) {
            throw new InvalidRoomRequestException("El edificio no tiene un auxiliar áulico asignado.");
        }

        Set<Long> occupiedClassroomIds = candidateResolver.occupiedClassroomIds(item);
        boolean hasFreeClassroom = candidateResolver.candidateClassrooms(item).stream()
                .anyMatch(c -> c.buildingId().equals(buildingId) && !occupiedClassroomIds.contains(c.id()));
        if (!hasFreeClassroom) {
            throw new InvalidRoomRequestException(
                    "El edificio no tiene ninguna aula libre que cumpla los requisitos del pedido.");
        }

        item.deriveTo(buildingId, actor, LocalDateTime.now());

        log.info("Pedido de aula derivado: itemId={}, buildingId={}", itemId, buildingId);
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto returnItem(Long itemId, String reason, String actor) {
        log.debug("Devolviendo pedido de aula desde su edificio: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de devolución es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.NEW);

        item.returnFromBuilding(reason);

        log.info("Pedido de aula devuelto: itemId={}", itemId);
        return composer.composeItem(item);
    }

    // TODO: este método va a cambiar cuando se agregue el módulo "notification" que consuma RoomRequestResolved.
    @Override
    @Transactional
    public RoomRequestItemResponseDto notify(Long itemId, String actor) {
        log.debug("Notificando pedido de aula: itemId={}", itemId);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));

        if (item.getNotifiedAt() != null) {
            log.debug("Pedido ya notificado, devuelve el estado actual sin resellar: itemId={}", itemId);
            return composer.composeItem(item);
        }

        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.RESOLVED);
        if (item.getAllocations().isEmpty()) {
            throw new InvalidRoomRequestException("El pedido no tiene ninguna aula asignada.");
        }

        LocalDateTime now = LocalDateTime.now();
        item.resolve(actor, now);

        List<Long> classroomIds = item.getAllocations().stream()
                .map(RoomRequestItemAllocation::getClassroomId)
                .distinct()
                .toList();
        eventPublisher.publishEvent(new RoomRequestResolved(item.getId(), item.getRequest().getId(),
                item.getRequest().getTeacherName(), item.getRequest().getTeacherEmail(),
                item.getRequest().getSubjectId(), item.getDate(), item.getDayOfWeek(), item.getStartTime(),
                classroomIds));

        log.info("Pedido de aula notificado: itemId={}", itemId);
        return composer.composeItem(item);
    }
}
