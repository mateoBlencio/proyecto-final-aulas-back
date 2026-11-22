package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestNotificationModel;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Encola el mail de pedido resuelto una vez confirmada la transacción de {@code notify}, en una transacción
 * propia (REQUIRES_NEW): si el render o el encolado fallan, el pedido ya notificado no se revierte. La
 * transacción va por {@link TransactionTemplate} y no con {@code @Transactional} porque el try/catch tiene que
 * quedar fuera de ella: una excepción que cruza un proxy transaccional deja la transacción rollback-only y el
 * commit del proxy lanzaría {@code UnexpectedRollbackException} hacia quien confirmó {@code notify}.
 */
@Slf4j
@Component
class RoomRequestNotifiedMailListener {

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestComposer composer;
    private final RoomRequestNotificationModel notificationModel;
    private final UserService userService;
    private final NotificationSender notificationSender;
    private final TransactionTemplate isolated;

    RoomRequestNotifiedMailListener(RoomRequestItemRepository itemRepository, RoomRequestComposer composer,
                                    RoomRequestNotificationModel notificationModel, UserService userService,
                                    NotificationSender notificationSender,
                                    PlatformTransactionManager transactionManager) {
        this.itemRepository = itemRepository;
        this.composer = composer;
        this.notificationModel = notificationModel;
        this.userService = userService;
        this.notificationSender = notificationSender;
        this.isolated = new TransactionTemplate(transactionManager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RoomRequestItemNotified event) {
        try {
            isolated.executeWithoutResult(status -> send(event));
        } catch (RuntimeException e) {
            log.error("No se pudo encolar la notificación de pedido resuelto: itemId={}", event.itemId(), e);
        }
    }

    private void send(RoomRequestItemNotified event) {
        RoomRequestItem item = itemRepository.findWithRequestById(event.itemId()).orElse(null);
        if (item == null) {
            log.warn("Pedido de aula inexistente al enviar la notificación: itemId={}", event.itemId());
            return;
        }
        RoomRequestItemDetailDto detail = composer.composeDetail(item);
        notificationSender.send(new NotificationRequest(
                NotificationTemplate.ROOM_REQUEST_RESOLVED,
                List.of(new NotificationRecipient(detail.request().teacherName(), detail.request().teacherEmail())),
                notificationModel.build(detail),
                "room-request-item:" + event.itemId() + ":NOTIFIED:" + event.round(),
                replyToFor(item)));
    }

    private List<String> replyToFor(RoomRequestItem item) {
        if (item.getDerivedBuildingId() == null) {
            return List.of();
        }
        return userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, item.getDerivedBuildingId()).stream()
                .map(UserResponseDto::email)
                .toList();
    }
}
