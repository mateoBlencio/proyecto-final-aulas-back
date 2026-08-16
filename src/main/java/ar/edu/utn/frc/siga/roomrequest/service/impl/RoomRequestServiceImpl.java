package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestServiceImpl implements RoomRequestService {

    private final RoomRequestRepository repository;
    private final RoomRequestComposer composer;
    private final RoomRequestValidator validator;

    @Override
    @Transactional
    public RoomRequestResponseDto create(CreateRoomRequestDto dto) {
        log.debug("Creando solicitud de aula: type={}, scope={}, subjectId={}, items={}",
                dto.type(), dto.scope(), dto.subjectId(), dto.items().size());

        validator.validateForCreation(dto);

        RoomRequest request = RoomRequest.builder()
                .type(dto.type())
                .scope(dto.scope())
                .teacherName(dto.teacherName())
                .teacherEmail(dto.teacherEmail())
                .teacherPhone(dto.teacherPhone())
                .subjectId(dto.subjectId())
                .createdAt(LocalDateTime.now())
                .build();

        dto.items().forEach(item -> request.addItem(toItem(item)));

        RoomRequest saved = repository.save(request);
        log.info("Solicitud de aula creada: id={}, type={}, items={}",
                saved.getId(), saved.getType(), saved.getItems().size());
        return composer.compose(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomRequestResponseDto findById(Long id) {
        return composer.compose(Finder.orThrow(repository::findWithItemsById, id, "RoomRequest"));
    }

    /**
     * Pasa un pedido a PRE_APPROVED. Hoy <b>solo cambia el estado</b> y deja
     * registrado quién decidió y cuándo.
     *
     * <p>TODO — a definir con el equipo: qué más tiene que hacer una
     * pre-aprobación. Lo más probable es que en el futuro dispare la creación
     * del evento académico y/o la asignación de aula. Cuando se defina, esto
     * pasa a orquestar {@code events :: api} y {@code allocation :: api} dentro
     * de esta misma transacción, y hay que agregar esas dependencias al
     * {@code package-info} del módulo. Se dejó afuera a propósito porque
     * PRE_APPROVED es un paso preliminar: materializar un evento en un estado
     * que todavía no es la aprobación final sería prematuro.
     */
    @Override
    @Transactional
    public RoomRequestResponseDto preApproveItem(Long requestId, Long itemId, String decidedBy, String reason) {
        return decideItem(requestId, itemId, RoomRequestStatus.PRE_APPROVED, decidedBy, reason);
    }

    @Override
    @Transactional
    public RoomRequestResponseDto cancelItem(Long requestId, Long itemId, String decidedBy, String reason) {
        return decideItem(requestId, itemId, RoomRequestStatus.CANCELLED, decidedBy, reason);
    }

    /**
     * Se entra siempre por la cabecera, que es la raíz del agregado: así el
     * ítem queda validado como propio de esa solicitud, y se devuelve la
     * solicitud completa para que quien decide vea cómo quedaron los demás
     * pedidos.
     */
    private RoomRequestResponseDto decideItem(Long requestId, Long itemId, RoomRequestStatus target,
                                              String decidedBy, String reason) {
        RoomRequest request = Finder.orThrow(repository::findWithItemsById, requestId, "RoomRequest");
        RoomRequestItem item = request.findItem(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));

        validator.validateTransition(item.getStatus(), target);

        item.decide(target, decidedBy, reason, LocalDateTime.now());
        log.info("Pedido de solicitud decidido: requestId={}, itemId={}, status={}, decidedBy={}",
                requestId, itemId, target, decidedBy);
        return composer.compose(request);
    }

    private RoomRequestItem toItem(CreateRoomRequestItemDto dto) {
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(dto.commissionId())
                .date(dto.date())
                .startTime(dto.startTime())
                .duration(Duration.between(dto.startTime(), dto.endTime()))
                .enrolled(dto.enrolled())
                .estimated(dto.estimated())
                .classroomCount(dto.classroomCount())
                .currentClassroomId(dto.currentClassroomId())
                .requiresProjector(Boolean.TRUE.equals(dto.requiresProjector()))
                .requiresComputers(Boolean.TRUE.equals(dto.requiresComputers()))
                .computerCount(dto.computerCount())
                .requiresExamUsers(Boolean.TRUE.equals(dto.requiresExamUsers()))
                .requiredSoftware(dto.requiredSoftware())
                .observations(dto.observations())
                .build();

        List<Integer> preferred = dto.preferredClassroomIds();
        if (preferred != null) {
            preferred.forEach(item::addPreference);
        }
        return item;
    }
}
