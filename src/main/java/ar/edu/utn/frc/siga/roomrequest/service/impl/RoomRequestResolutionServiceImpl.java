package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestResolutionServiceImpl implements RoomRequestResolutionService {

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestTransitionValidator transitionValidator;
    private final AllocationService allocationService;
    private final RoomRequestComposer composer;

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
}
