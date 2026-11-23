package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestNotificationModel;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestNotifiedMailListener")
class RoomRequestNotifiedMailListenerTest {

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private RoomRequestComposer composer;
    @Mock
    private RoomRequestNotificationModel notificationModel;
    @Mock
    private UserService userService;
    @Mock
    private NotificationSender notificationSender;
    @Mock
    private PlatformTransactionManager transactionManager;

    private RoomRequestNotifiedMailListener listener;

    @BeforeEach
    void setUp() {
        stubTransaction();
        listener = new RoomRequestNotifiedMailListener(itemRepository, composer, notificationModel, userService,
                notificationSender, transactionManager);
    }

    @Test
    @DisplayName("camino feliz: encola ROOM_REQUEST_RESOLVED al docente con la clave de idempotencia de la ronda del evento (no la del ítem)")
    void caminoFeliz() {
        RoomRequestItem item = item(null);
        stubItem(item);
        Map<String, Object> model = Map.of("clave", "valor");
        RoomRequestItemDetailDto detail = detail();
        when(composer.composeDetail(item)).thenReturn(detail);
        when(notificationModel.build(detail)).thenReturn(model);

        listener.on(new RoomRequestItemNotified(1L, 3, "subsecretaria@frc.utn.edu.ar"));

        NotificationRequest request = sentRequest();
        assertThat(request.template()).isEqualTo(NotificationTemplate.ROOM_REQUEST_RESOLVED);
        assertThat(request.idempotencyKey()).isEqualTo("room-request-item:1:NOTIFIED:3");
        assertThat(request.recipients()).containsExactly(new NotificationRecipient("Ada Lovelace", "ada@frc.utn.edu.ar"));
        assertThat(request.model()).isSameAs(model);
    }

    @Test
    @DisplayName("sin edificio derivado: replyTo vacío y no consulta a los auxiliares")
    void sinEdificioDerivado_replyToVacio() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());

        listener.on(new RoomRequestItemNotified(1L, 1, "subsecretaria@frc.utn.edu.ar"));

        assertThat(sentRequest().replyTo()).isEmpty();
        verify(userService, never()).findByRoleForBuilding(any(), any());
    }

    @Test
    @DisplayName("actor auxiliar: CC = el actor y CCO = todas las subsecretarias habilitadas")
    void actorAuxiliar_ccElActorYCcoTodasLasSubsecretarias() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(userService.findByRole(SystemRole.ADMINISTRADOR)).thenReturn(List.of(
                user(1L, "subsecretaria1@frc.utn.edu.ar"), user(2L, "subsecretaria2@frc.utn.edu.ar")));

        listener.on(new RoomRequestItemNotified(1L, 1, "auxiliar@frc.utn.edu.ar"));

        NotificationRequest request = sentRequest();
        assertThat(request.cc()).containsExactly("auxiliar@frc.utn.edu.ar");
        assertThat(request.bcc()).containsExactly("subsecretaria1@frc.utn.edu.ar", "subsecretaria2@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("actor subsecretaria: CC = el actor y CCO = las demás, sin el actor aunque el email difiera en mayúsculas")
    void actorSubsecretaria_ccoSinElActorSinDistinguirMayusculas() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(userService.findByRole(SystemRole.ADMINISTRADOR)).thenReturn(List.of(
                user(1L, "subsecretaria@frc.utn.edu.ar"), user(2L, "otra@frc.utn.edu.ar")));

        listener.on(new RoomRequestItemNotified(1L, 1, "Subsecretaria@FRC.utn.edu.ar"));

        NotificationRequest request = sentRequest();
        assertThat(request.cc()).containsExactly("Subsecretaria@FRC.utn.edu.ar");
        assertThat(request.bcc()).containsExactly("otra@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("sin subsecretarias habilitadas: CCO vacío y el actor sigue en CC")
    void sinSubsecretariasHabilitadas_ccoVacio() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(userService.findByRole(SystemRole.ADMINISTRADOR)).thenReturn(List.of());

        listener.on(new RoomRequestItemNotified(1L, 1, "auxiliar@frc.utn.edu.ar"));

        NotificationRequest request = sentRequest();
        assertThat(request.cc()).containsExactly("auxiliar@frc.utn.edu.ar");
        assertThat(request.bcc()).isEmpty();
    }

    @Test
    @DisplayName("la única subsecretaria habilitada es el actor: CCO vacío")
    void unicaSubsecretariaEsElActor_ccoVacio() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(userService.findByRole(SystemRole.ADMINISTRADOR))
                .thenReturn(List.of(user(1L, "subsecretaria@frc.utn.edu.ar")));

        listener.on(new RoomRequestItemNotified(1L, 1, "subsecretaria@frc.utn.edu.ar"));

        assertThat(sentRequest().bcc()).isEmpty();
    }

    @Test
    @DisplayName("con edificio derivado: el replyTo sigue siendo solo los auxiliares del edificio, sin las copias")
    void conEdificioDerivado_replyToNoSeMezclaConLasCopias() {
        RoomRequestItem item = item(7L);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 7L))
                .thenReturn(List.of(user(1L, "auxiliar@frc.utn.edu.ar")));
        when(userService.findByRole(SystemRole.ADMINISTRADOR))
                .thenReturn(List.of(user(2L, "subsecretaria@frc.utn.edu.ar")));

        listener.on(new RoomRequestItemNotified(1L, 1, "auxiliar@frc.utn.edu.ar"));

        NotificationRequest request = sentRequest();
        assertThat(request.replyTo()).containsExactly("auxiliar@frc.utn.edu.ar");
        assertThat(request.cc()).containsExactly("auxiliar@frc.utn.edu.ar");
        assertThat(request.bcc()).containsExactly("subsecretaria@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("con edificio derivado: replyTo con los emails de todos los auxiliares del edificio")
    void conEdificioDerivado_replyToConLosAuxiliares() {
        RoomRequestItem item = item(7L);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 7L)).thenReturn(List.of(
                new UserResponseDto(1L, "auxiliar@frc.utn.edu.ar", "Auxiliar", "Aulico", List.of()),
                new UserResponseDto(2L, "otro@frc.utn.edu.ar", "Otro", "Auxiliar", List.of())));

        listener.on(new RoomRequestItemNotified(1L, 1, "subsecretaria@frc.utn.edu.ar"));

        assertThat(sentRequest().replyTo()).containsExactly("auxiliar@frc.utn.edu.ar", "otro@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("el envío corre en una transacción REQUIRES_NEW que se confirma")
    void enviaEnTransaccionPropia() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());

        listener.on(new RoomRequestItemNotified(1L, 1, "subsecretaria@frc.utn.edu.ar"));

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("si send lanza, la excepción no se propaga y la transacción propia se revierte")
    void falloDelEnvio_noSePropaga() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        doThrow(new IllegalStateException("smtp caído")).when(notificationSender).send(any());

        assertThatCode(() -> listener.on(new RoomRequestItemNotified(1L, 1, "subsecretaria@frc.utn.edu.ar"))).doesNotThrowAnyException();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("si el render del modelo lanza, tampoco se propaga ni se envía nada")
    void falloDelModelo_noSePropaga() {
        RoomRequestItem item = item(null);
        stubItem(item);
        when(composer.composeDetail(item)).thenReturn(detail());
        when(notificationModel.build(any())).thenThrow(new IllegalStateException("plantilla rota"));

        assertThatCode(() -> listener.on(new RoomRequestItemNotified(1L, 1, "subsecretaria@frc.utn.edu.ar"))).doesNotThrowAnyException();

        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("ítem inexistente: no envía y no lanza")
    void itemInexistente_noEnvia() {
        when(itemRepository.findWithRequestById(99L)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.on(new RoomRequestItemNotified(99L, 1, "subsecretaria@frc.utn.edu.ar"))).doesNotThrowAnyException();

        verifyNoInteractions(notificationSender, composer, notificationModel, userService);
    }

    private void stubTransaction() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
    }

    private void stubItem(RoomRequestItem item) {
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
    }

    private NotificationRequest sentRequest() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(captor.capture());
        return captor.getValue();
    }

    private static UserResponseDto user(Long id, String email) {
        return new UserResponseDto(id, email, "Nombre", "Apellido", List.of());
    }

    private static RoomRequestItem item(Long derivedBuildingId) {
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.FINAL_EXAM)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(1L)
                .build();
        return RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NOTIFIED).request(request)
                .derivedBuildingId(derivedBuildingId).notificationRound(9).build();
    }

    private static RoomRequestItemDetailDto detail() {
        RoomRequestItemDetailHeaderDto header = new RoomRequestItemDetailHeaderDto(1L, RoomRequestType.FINAL_EXAM,
                AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567", null, null, 1);
        RoomRequestItemResponseDto response = new RoomRequestItemResponseDto(1L, 1, RoomRequestStatus.NOTIFIED,
                "subsecretaria@frc.utn.edu.ar", null, null, null, List.of(), null, null, null, null, null, null, null, 1,
                false, false, null, false, null, null, List.of(), null, List.of(), null, null, null, null, null, null,
                null, null, null, List.of(), null);
        return new RoomRequestItemDetailDto(header, response, List.of(), List.of());
    }
}
