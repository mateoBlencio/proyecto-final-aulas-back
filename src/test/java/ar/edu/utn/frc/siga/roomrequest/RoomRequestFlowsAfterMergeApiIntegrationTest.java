package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Pedidos de aula: reasignar y cancelar después de fusionar una clase por baja de aula (integración)")
class RoomRequestFlowsAfterMergeApiIntegrationTest extends AbstractRoomRequestDeactivationIntegrationTest {

    private record Merged(ChangeRequest request, Long mirrorId, Classroom r205) {}

    private Merged mergedRequest(LocalDate date) throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        ChangeRequest request = oneTimeChangeWithTwoClassrooms(date);
        assign(request.itemId(), r204, r205).andExpect(status().isOk());
        Long mirrorId = mirrorsOf(request.occurrenceId()).getFirst().getId();
        deactivateClassroom(r204).andExpect(status().isOk()).andExpect(jsonPath("$.mergedClasses").value(1));
        assertAllocatedIn(request.occurrenceId(), r205);
        assertMirrorStaysRetired(mirrorId);
        return new Merged(request, mirrorId, r205);
    }

    private List<Occurrence> mirrorsOf(Long principalId) {
        return occurrenceRepository.findAll().stream()
                .filter(o -> principalId.equals(o.getMirrorOfOccurrenceId()))
                .toList();
    }

    private void assertMirrorStaysRetired(Long mirrorId) {
        Occurrence mirror = occurrence(mirrorId);
        assertThat(mirror.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(mirror.getReleaseReason()).isEqualTo(ReleaseReason.MIRROR_RETIRED);
        assertThat(allocationOf(mirrorId)).isEmpty();
    }

    @Test
    @DisplayName("reasignar el pedido a otras dos aulas responde 200 y el espejo retirado sigue MIRROR_RETIRED sin asignacion")
    void reasignarNoReviveElEspejoRetirado() throws Exception {
        Merged merged = mergedRequest(LocalDate.now().plusDays(60));
        Building building = testData.edificio();
        Classroom r301 = testData.aula(building);
        Classroom r302 = testData.aula(building);

        assign(merged.request().itemId(), r301, r302)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));

        assertMirrorStaysRetired(merged.mirrorId());
        assertAllocatedIn(merged.request().occurrenceId(), r301);
    }

    @Test
    @DisplayName("cancelar el pedido responde 200 y el espejo retirado sigue MIRROR_RETIRED sin asignacion")
    void cancelarNoReviveElEspejoRetirado() throws Exception {
        Merged merged = mergedRequest(LocalDate.now().plusDays(61));

        mockMvc.perform(post("/v1/room-requests/items/{id}/cancel", merged.request().itemId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"el docente desistio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertMirrorStaysRetired(merged.mirrorId());
    }
}
