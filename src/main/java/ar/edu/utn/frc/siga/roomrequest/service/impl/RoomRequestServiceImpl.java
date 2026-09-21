package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemStatusCountDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.handler.RoomRequestHandlers;
import ar.edu.utn.frc.siga.roomrequest.handler.RoomRequestTypeHandler;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.roomrequest.specification.RoomRequestItemSort;
import ar.edu.utn.frc.siga.roomrequest.specification.RoomRequestItemSpecification;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestServiceImpl implements RoomRequestService {

    private final RoomRequestRepository repository;
    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestComposer composer;
    private final RoomRequestHandlers handlers;
    private final RoomRequestAccessControl accessControl;

    @Override
    @Transactional
    @AuditOperation("Alta de solicitud de aula")
    public RoomRequestResponseDto create(CreateRoomRequestDto dto) {
        log.debug("Creando solicitud de aula: teacherName={}, type={}, scope={}, subjectId={}, items={}",
                dto.requester().teacherName(), dto.type(), dto.requester().scope(),
                dto.subjectId(), dto.items().size());

        RoomRequestTypeHandler handler = handlers.forType(dto.type());
        handler.validate(dto);
        RoomRequest saved = repository.save(handler.assemble(dto));

        log.info("Solicitud de aula creada: id={}, type={}, items={}",
                saved.getId(), saved.getType(), saved.getItems().size());
        return composer.compose(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoomRequestItemRowDto> findItems(RoomRequestItemFilter filter, Pageable pageable,
            String actorEmail) {
        log.debug("Listando pedidos de aula: types={}, statuses={}, scope={}, subjectId={}, "
                        + "dateFrom={}, dateTo={}, includePast={}",
                filter.types(), filter.statuses(), filter.scope(), filter.subjectId(),
                filter.dateFrom(), filter.dateTo(), filter.includePast());

        RoomRequestItemFilter scoped = scoped(filter, actorEmail);
        Page<RoomRequestItem> page = itemRepository.findAll(
                RoomRequestItemSpecification.withFilter(scoped), RoomRequestItemSort.apply(pageable));
        Page<RoomRequestItemRowDto> result =
                new PageImpl<>(composer.composeRows(page.getContent()), page.getPageable(), page.getTotalElements());
        log.info("Pedidos de aula listados: total={}", result.getTotalElements());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomRequestItemStatusCountDto> countItemsByStatus(boolean includePast,
            Boolean requiresSpecialAssignment, Boolean partiallyResolved, String actorEmail) {
        log.debug("Contando pedidos de aula por estado: includePast={}, requiresSpecialAssignment={}, "
                        + "partiallyResolved={}", includePast, requiresSpecialAssignment, partiallyResolved);

        RoomRequestItemFilter base = scoped(RoomRequestItemFilter.of(null, null, null, null, null, null, includePast,
                requiresSpecialAssignment, null, partiallyResolved, null), actorEmail);
        List<RoomRequestItemStatusCountDto> counts = Arrays.stream(RoomRequestStatus.values())
                .map(status -> new RoomRequestItemStatusCountDto(status, itemRepository.count(
                        RoomRequestItemSpecification.withFilter(onlyStatus(base, status)))))
                .toList();
        log.info("Pedidos de aula contados por estado: {}", counts);
        return counts;
    }

    private RoomRequestItemFilter scoped(RoomRequestItemFilter filter, String actorEmail) {
        return accessControl.readScope(actorEmail).map(filter::restrictedToBuildings).orElse(filter);
    }

    private static RoomRequestItemFilter onlyStatus(RoomRequestItemFilter base, RoomRequestStatus status) {
        return new RoomRequestItemFilter(null, Set.of(status), null, null,
                base.dateFrom(), base.dateTo(), base.includePast(),
                base.requiresSpecialAssignment(), null, base.partiallyResolved(), null,
                base.restrictToBuildingIds());
    }

    @Override
    @Transactional(readOnly = true)
    public RoomRequestItemDetailDto findItemById(Long itemId, String actorEmail) {
        log.debug("Buscando pedido de aula por id={}", itemId);
        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        accessControl.authorizeRead(item, actorEmail);
        return composer.composeDetail(item);
    }
}
