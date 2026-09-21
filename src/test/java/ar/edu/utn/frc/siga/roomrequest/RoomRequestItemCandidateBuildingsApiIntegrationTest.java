package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items/{id}/candidate-buildings (integración)")
class RoomRequestItemCandidateBuildingsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("edificio con aula libre y auxiliar áulico asignado: aparece con availableClassroomCount")
    void candidateBuildings_edificioConAuxiliarAsignado() throws Exception {
        Building building = testData.edificio();
        testData.aula(building);
        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
        RoomRequestItem item = seedItem();

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/candidate-buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + building.getId() + ")].availableClassroomCount").value(1));
    }

    // El caso "edificio sin auxiliar asignado no aparece" no es testeable acá de forma confiable:
    // otros *ApiIntegrationTest del módulo autentican como AUXILIAR_AULICO con mockMvcAs(...), que
    // crea el fixture con scope GLOBAL -- cubre todos los edificios para el resto de la corrida de
    // la suite. Queda cubierto por RoomRequestResolutionServiceImplTest.findCandidateBuildings_sinAuxiliarAsignado
    // (unit, con userService mockeado).

    @Test
    @DisplayName("id inexistente: 404")
    void candidateBuildings_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(get("/v1/room-requests/items/" + unknownId + "/candidate-buildings"))
                .andExpect(status().isNotFound());
    }

    private RoomRequestItem seedItem() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(academic.commissionId())
                .date(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(35)
                .classroomCount(1)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }
}
