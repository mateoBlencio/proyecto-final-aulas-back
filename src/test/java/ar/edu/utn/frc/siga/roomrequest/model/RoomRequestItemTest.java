package ar.edu.utn.frc.siga.roomrequest.model;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestItem")
class RoomRequestItemTest {

    @Test
    @DisplayName("item sin aula anterior: la primera llamada la graba")
    void firstCallRecordsClassroom() {
        RoomRequestItem item = RoomRequestItem.builder().build();

        item.rememberPreviousClassroom(5L);

        assertThat(item.getPreviousClassroomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("ya hay aula anterior: una segunda llamada no la pisa")
    void secondCallDoesNotOverwrite() {
        RoomRequestItem item = RoomRequestItem.builder().build();

        item.rememberPreviousClassroom(5L);
        item.rememberPreviousClassroom(9L);

        assertThat(item.getPreviousClassroomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("notifyTeacher: pasa a NOTIFIED, sella notifiedAt y plazo y suma una ronda cada vez")
    void notifyTeacherSumaRonda() {
        RoomRequestItem item = RoomRequestItem.builder().status(RoomRequestStatus.IN_EVALUATION).build();
        Instant now = Instant.parse("2027-03-10T12:00:00Z");
        Instant deadline = Instant.parse("2027-03-12T12:00:00Z");

        item.notifyTeacher("sub@frc.utn.edu.ar", now, deadline);
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NOTIFIED);
        assertThat(item.getNotifiedAt()).isEqualTo(now);
        assertThat(item.getAcceptanceDeadline()).isEqualTo(deadline);
        assertThat(item.getNotificationRound()).isEqualTo(1);

        item.reopen("otra aula", "sub@frc.utn.edu.ar", now.plusSeconds(60));
        item.notifyTeacher("sub@frc.utn.edu.ar", now.plusSeconds(120), deadline);
        assertThat(item.getNotificationRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("reopen: conserva notifiedAt y la ronda; limpia plazo y aceptación; guarda el motivo")
    void reopenLimpiaAceptacion() {
        RoomRequestItem item = RoomRequestItem.builder().status(RoomRequestStatus.IN_EVALUATION).build();
        Instant notifiedAt = Instant.parse("2027-03-10T12:00:00Z");
        item.notifyTeacher("sub@frc.utn.edu.ar", notifiedAt, notifiedAt.plusSeconds(3600));
        item.accept("SISTEMA", notifiedAt.plusSeconds(7200));
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(item.getAcceptanceDeadline()).isNull();

        item.reopen("proyector roto", "sub@frc.utn.edu.ar", notifiedAt.plusSeconds(9000));

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getDecisionReason()).isEqualTo("proyector roto");
        assertThat(item.getNotifiedAt()).isEqualTo(notifiedAt);
        assertThat(item.getNotificationRound()).isEqualTo(1);
        assertThat(item.getAcceptedAt()).isNull();
        assertThat(item.getAcceptedBy()).isNull();
        assertThat(item.getAcceptanceDeadline()).isNull();
    }

    @Test
    @DisplayName("assignClassrooms: conserva las filas de clases pasadas y solo borra los espejos retirados")
    void assignClassroomsConservaPasadas() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();
        item.assignClassrooms(List.of(305L, 306L), List.of(List.of(1L, 2L, 3L), List.of(11L, 12L, 13L)), List.of(), Map.of());

        // Se reasigna a una sola aula: 1 y 11 ya pasaron, 2 y 3 siguen vigentes, 12 y 13 son espejos futuros retirados.
        item.assignClassrooms(List.of(401L), List.of(List.of(2L, 3L)), List.of(12L, 13L), Map.of());

        assertThat(item.getAllocations())
                .extracting(RoomRequestItemAllocation::getOccurrenceId, RoomRequestItemAllocation::getClassroomId,
                        RoomRequestItemAllocation::getPosition)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 305L, 1),
                        org.assertj.core.groups.Tuple.tuple(2L, 401L, 1),
                        org.assertj.core.groups.Tuple.tuple(3L, 401L, 1),
                        org.assertj.core.groups.Tuple.tuple(11L, 306L, 2));
    }

    private static RoomRequestItemAllocation.Original original(Long classroomId, AllocationSource source) {
        return new RoomRequestItemAllocation.Original(classroomId, source);
    }

    private static RoomRequestItemAllocation rowOf(RoomRequestItem item, Long occurrenceId) {
        return item.getAllocations().stream().filter(a -> a.getOccurrenceId().equals(occurrenceId)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("assignClassrooms: guarda aula y origen originales solo en la fila principal, marcada como capturada")
    void assignClassroomsGuardaElOriginalSoloEnLaPrincipal() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();

        item.assignClassrooms(List.of(305L, 306L), List.of(List.of(1L), List.of(11L)), List.of(),
                Map.of(1L, original(100L, AllocationSource.SYSACAD), 11L, original(101L, AllocationSource.MANUAL)));

        RoomRequestItemAllocation principal = rowOf(item, 1L);
        assertThat(principal.getOriginalClassroomId()).isEqualTo(100L);
        assertThat(principal.getOriginalSource()).isEqualTo(AllocationSource.SYSACAD);
        assertThat(principal.isOriginalCaptured()).isTrue();
        RoomRequestItemAllocation mirror = rowOf(item, 11L);
        assertThat(mirror.getOriginalClassroomId()).isNull();
        assertThat(mirror.isOriginalCaptured()).isFalse();
    }

    @Test
    @DisplayName("assignClassrooms: una fecha sin aula previa queda capturada con original nulo")
    void assignClassroomsSinAulaPreviaQuedaCapturadaConOriginalNulo() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();

        item.assignClassrooms(List.of(305L), List.of(List.of(1L)), List.of(), Map.of());

        RoomRequestItemAllocation principal = rowOf(item, 1L);
        assertThat(principal.isOriginalCaptured()).isTrue();
        assertThat(principal.getOriginalClassroomId()).isNull();
        assertThat(item.originalClassroomOf(principal)).isNull();
    }

    @Test
    @DisplayName("assignClassrooms: una reasignación no pisa el original ya guardado")
    void assignClassroomsNoPisaElOriginal() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();
        item.assignClassrooms(List.of(305L), List.of(List.of(1L)), List.of(), Map.of(1L, original(100L, AllocationSource.SYSACAD)));

        item.assignClassrooms(List.of(401L), List.of(List.of(1L)), List.of(), Map.of(1L, original(305L, AllocationSource.MANUAL)));

        RoomRequestItemAllocation principal = rowOf(item, 1L);
        assertThat(principal.getClassroomId()).isEqualTo(401L);
        assertThat(principal.getOriginalClassroomId()).isEqualTo(100L);
        assertThat(principal.getOriginalSource()).isEqualTo(AllocationSource.SYSACAD);
    }

    @Test
    @DisplayName("assignClassrooms: limpia reabierto_desde porque reasignar anula el aula que el docente ya vio")
    void assignClassroomsLimpiaReabiertoDesde() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();
        item.markReopenedFromNotified(java.time.LocalDate.of(2027, 3, 1));

        item.assignClassrooms(List.of(305L), List.of(List.of(1L)), List.of(), Map.of());

        assertThat(item.getReopenedFromNotifiedOn()).isNull();
    }

    @Test
    @DisplayName("originalClassroomOf: una fila anterior a V60 usa el aula anterior del ítem con origen MANUAL")
    void originalDeFilaPreV60UsaElAulaAnterior() {
        RoomRequestItem item = RoomRequestItem.builder().previousClassroomId(77L).build();
        RoomRequestItemAllocation legacy = RoomRequestItemAllocation.builder().occurrenceId(1L).classroomId(305L)
                .position(1).originalCaptured(false).build();

        assertThat(item.originalClassroomOf(legacy)).isEqualTo(77L);
        assertThat(item.originalSourceOf(legacy)).isEqualTo(AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("originalClassroomOf: una fila capturada ignora el aula anterior del ítem y conserva el origen guardado")
    void originalDeFilaCapturadaIgnoraElAulaAnteriorDelItem() {
        RoomRequestItem item = RoomRequestItem.builder().previousClassroomId(77L).build();
        RoomRequestItemAllocation sinAula = RoomRequestItemAllocation.builder().occurrenceId(1L).classroomId(305L)
                .position(1).originalCaptured(true).build();
        RoomRequestItemAllocation conAula = RoomRequestItemAllocation.builder().occurrenceId(2L).classroomId(305L)
                .position(1).originalCaptured(true).originalClassroomId(100L)
                .originalSource(AllocationSource.SYSACAD).build();

        assertThat(item.originalClassroomOf(sinAula)).isNull();
        assertThat(item.originalClassroomOf(conAula)).isEqualTo(100L);
        assertThat(item.originalSourceOf(conAula)).isEqualTo(AllocationSource.SYSACAD);
    }

    @Test
    @DisplayName("moveToOriginal: mueve solo las filas principales indicadas y deja el resto")
    void moveToOriginalMueveSoloLasIndicadas() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();
        item.assignClassrooms(List.of(305L, 306L), List.of(List.of(1L, 2L), List.of(11L)), List.of(),
                Map.of(1L, original(100L, AllocationSource.MANUAL), 2L, original(101L, AllocationSource.MANUAL)));

        item.moveToOriginal(List.of(1L, 11L));

        assertThat(rowOf(item, 1L).getClassroomId()).isEqualTo(100L);
        assertThat(rowOf(item, 2L).getClassroomId()).isEqualTo(305L);
        assertThat(rowOf(item, 11L).getClassroomId()).isEqualTo(306L);
    }

    @Test
    @DisplayName("keepsNotifiedClassroom: verdadero solo si una fila relevante sigue en un aula distinta de su original")
    void keepsNotifiedClassroomSoloConFilasRelevantes() {
        RoomRequestItem item = RoomRequestItem.builder().allocations(new ArrayList<>()).build();
        item.assignClassrooms(List.of(305L), List.of(List.of(1L, 2L)), List.of(),
                Map.of(1L, original(100L, AllocationSource.MANUAL), 2L, original(305L, AllocationSource.MANUAL)));

        assertThat(item.keepsNotifiedClassroom(List.of(1L))).isTrue();
        assertThat(item.keepsNotifiedClassroom(List.of(2L))).isFalse();
        assertThat(item.keepsNotifiedClassroom(List.of())).isFalse();
    }
}
