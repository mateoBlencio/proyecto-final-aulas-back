package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Pedidos de aula: aulas dadas de baja en el detalle y en el impacto (integración)")
class RoomRequestUnavailableClassroomApiIntegrationTest extends AbstractRoomRequestDeactivationIntegrationTest {

    @Test
    @DisplayName("preferencia en un aula dada de baja: preferredClassrooms trae unavailableReason DEACTIVATED y el aula vigente trae null")
    void preferenciaEnAulaDadaDeBaja() throws Exception {
        Building building = testData.edificio();
        Classroom deactivated = testData.aula(building);
        Classroom available = testData.aula(building);
        var sc = testData.materiaYComision();
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM, sc, null, LocalDate.now().plusDays(40), 1,
                deactivated, available);
        deactivateClassroom(deactivated).andExpect(status().isOk());

        detail(item.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.preferredClassrooms[0].id").value(deactivated.getId().intValue()))
                .andExpect(jsonPath("$.item.preferredClassrooms[0].unavailableReason").value("DEACTIVATED"))
                .andExpect(jsonPath("$.item.preferredClassrooms[1].id").value(available.getId().intValue()))
                .andExpect(jsonPath("$.item.preferredClassrooms[1].unavailableReason").doesNotExist());
    }

    @Test
    @DisplayName("con el edificio dado de baja, la preferencia trae unavailableReason PARENT_DEACTIVATED")
    void preferenciaEnEdificioDadoDeBaja() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        var sc = testData.materiaYComision();
        RoomRequestItem item = item(RoomRequestType.FINAL_EXAM, sc, null, LocalDate.now().plusDays(41), 1, classroom);
        mockMvc.perform(delete("/v1/buildings/{id}/activation", building.getId())).andExpect(status().isOk());

        detail(item.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.preferredClassrooms[0].unavailableReason").value("PARENT_DEACTIVATED"));
    }

    @Test
    @DisplayName("tras fusionar la clase del pedido, currentClassrooms muestra el aula del espejo y assignedClassrooms conserva la 204 con DEACTIVATED")
    void pedidoDespuesDeFusionar() throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        ChangeRequest request = oneTimeChangeWithTwoClassrooms(LocalDate.now().plusDays(50));
        assign(request.itemId(), r204, r205).andExpect(status().isOk());
        assertThat(allocationOf(request.occurrenceId())).hasValueSatisfying(
                a -> assertThat(a.getClassroomId()).isEqualTo(r204.getId()));

        deactivateClassroom(r204).andExpect(status().isOk())
                .andExpect(jsonPath("$.mergedClasses").value(1))
                .andExpect(jsonPath("$.releasedClasses").value(0));

        assertAllocatedIn(request.occurrenceId(), r205);
        detail(request.itemId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.currentClassrooms.length()").value(1))
                .andExpect(jsonPath("$.item.currentClassrooms[0].id").value(r205.getId().intValue()))
                .andExpect(jsonPath("$.item.assignedClassrooms[?(@.id==" + r204.getId() + ")].unavailableReason")
                        .value("DEACTIVATED"))
                .andExpect(jsonPath("$.item.assignedClassrooms[?(@.id==" + r205.getId() + ")]").exists());
    }

    @Test
    @DisplayName("la baja cuenta los pedidos abiertos que piden el aula y los detalla con docente, materia y aulas afectadas")
    void impactoCuentaLosPedidosQuePidenElAula() throws Exception {
        Building building = testData.edificio();
        Classroom requested = testData.aula(building);
        Classroom notRequested = testData.aula(building);
        var sc = testData.materiaYComision();
        RoomRequestItem open = item(RoomRequestType.FINAL_EXAM, sc, null, LocalDate.now().plusDays(42), 1, requested);
        item(RoomRequestType.FINAL_EXAM, sc, null, LocalDate.now().plusDays(43), 1, notRequested);
        mockMvc.perform(post("/v1/room-requests/items/{id}/cancel", open.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no va\"}"))
                .andExpect(status().isOk());
        RoomRequestItem stillOpen = item(RoomRequestType.FINAL_EXAM, sc, null, LocalDate.now().plusDays(44), 1,
                requested);

        classroomImpact(requested)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedRoomRequests").value(1))
                .andExpect(jsonPath("$.visibleRoomRequests.length()").value(1))
                .andExpect(jsonPath("$.visibleRoomRequests[0].itemId").value(stillOpen.getId().intValue()))
                .andExpect(jsonPath("$.visibleRoomRequests[0].status").value("NEW"))
                .andExpect(jsonPath("$.visibleRoomRequests[0].teacherName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.visibleRoomRequests[0].classroomIds[0]").value(requested.getId().intValue()));
    }
}
