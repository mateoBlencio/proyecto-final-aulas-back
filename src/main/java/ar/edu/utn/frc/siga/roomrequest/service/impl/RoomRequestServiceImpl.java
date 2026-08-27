package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestMapper;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.roomrequest.specification.RoomRequestItemSort;
import ar.edu.utn.frc.siga.roomrequest.specification.RoomRequestItemSpecification;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestServiceImpl implements RoomRequestService {

    private final RoomRequestRepository repository;
    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestMapper mapper;
    private final RoomRequestComposer composer;
    private final RoomRequestValidator validator;

    @Override
    @Transactional
    public RoomRequestResponseDto create(CreateRoomRequestDto dto) {
        log.debug("Creando solicitud de aula: teacherName={}, type={}, scope={}, subjectId={}, items={}",
                dto.teacherName(), dto.type(), dto.scope(), dto.subjectId(), dto.items().size());

        validator.validateForCreation(dto);

        RoomRequest request = mapper.toEntity(dto);
        for (CreateRoomRequestItemDto itemDto : dto.items()) {
            RoomRequestItem item = mapper.toEntity(itemDto);
            item.addPreferences(itemDto.preferredClassroomIds());
            request.addItem(item);
        }

        RoomRequest saved = repository.save(request);
        log.info("Solicitud de aula creada: id={}, type={}, items={}",
                saved.getId(), saved.getType(), saved.getItems().size());
        return composer.compose(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoomRequestItemRowDto> findItems(RoomRequestItemFilter filter, Pageable pageable) {
        log.debug("Listando pedidos de aula: types={}, statuses={}, scope={}, subjectId={}, "
                        + "dateFrom={}, dateTo={}, includePast={}",
                filter.types(), filter.statuses(), filter.scope(), filter.subjectId(),
                filter.dateFrom(), filter.dateTo(), filter.includePast());

        Page<RoomRequestItem> page = itemRepository.findAll(
                RoomRequestItemSpecification.withFilter(filter), RoomRequestItemSort.apply(pageable));
        Page<RoomRequestItemRowDto> result =
                new PageImpl<>(composer.composeRows(page.getContent()), page.getPageable(), page.getTotalElements());
        log.info("Pedidos de aula listados: total={}", result.getTotalElements());
        return result;
    }
}
