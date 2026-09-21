package ar.edu.utn.frc.siga.roomrequest.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestItem.rememberPreviousClassroom")
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
}
