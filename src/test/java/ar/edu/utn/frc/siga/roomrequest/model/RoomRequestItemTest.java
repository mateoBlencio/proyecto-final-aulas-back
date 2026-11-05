package ar.edu.utn.frc.siga.roomrequest.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        item.assignClassrooms(List.of(305L, 306L), List.of(List.of(1L, 2L, 3L), List.of(11L, 12L, 13L)), List.of());

        // Se reasigna a una sola aula: 1 y 11 ya pasaron, 2 y 3 siguen vigentes, 12 y 13 son espejos futuros retirados.
        item.assignClassrooms(List.of(401L), List.of(List.of(2L, 3L)), List.of(12L, 13L));

        assertThat(item.getAllocations())
                .extracting(RoomRequestItemAllocation::getOccurrenceId, RoomRequestItemAllocation::getClassroomId,
                        RoomRequestItemAllocation::getPosition)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 305L, 1),
                        org.assertj.core.groups.Tuple.tuple(2L, 401L, 1),
                        org.assertj.core.groups.Tuple.tuple(3L, 401L, 1),
                        org.assertj.core.groups.Tuple.tuple(11L, 306L, 2));
    }
}
