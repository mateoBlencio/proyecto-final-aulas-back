package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("Baja lógica en cascada del módulo space (integración)")
class SpaceSoftDeleteCascadeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private ResourceTypeRepository resourceTypeRepository;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private BuildingService buildingService;

    private List<Long> availableIds() {
        return asFixtureUser(() -> classroomService.findAllAvailable()).stream()
                .map(ClassroomResponseDto::id).toList();
    }

    @Test
    @DisplayName("un aula activa de un edificio inhabilitado no se lista, no es candidata ni se resuelve, y vuelve al reactivar el edificio")
    void classroomOfInactiveBuilding_isHiddenAndComesBackWhenBuildingIsRestored() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long id = classroom.getId();
        assertThat(availableIds()).contains(id);

        building.deactivate();
        building = buildingRepository.save(building);

        assertThat(classroom.isActive()).isTrue();
        assertThat(availableIds()).doesNotContain(id);
        assertThat(classroomService.findByIds(List.of(id))).isEmpty();
        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id.intValue()))));
        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("includeDeactivated", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id.intValue())));

        buildingRepository.restore(building);

        assertThat(availableIds()).contains(id);
        assertThat(classroomService.findByIds(List.of(id))).hasSize(1);
    }

    @Test
    @DisplayName("un aula de un edificio inhabilitado se sigue resolviendo con nombre para referencias históricas")
    void classroomOfInactiveBuilding_isStillResolvableIncludingDeactivated() {
        Building building = testData.edificio("Edificio-Hist", false);
        Classroom classroom = testData.aula(building);

        List<ClassroomResponseDto> resolved = classroomService.findByIdsIncludingDeactivated(List.of(classroom.getId()));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().buildingName()).isEqualTo(building.getName());
        assertThat(resolved.getFirst().roomNumber()).isEqualTo(classroom.getRoomNumber());
    }

    @Test
    @DisplayName("un aula inhabilitada por sí misma tampoco es candidata (control de la regla previa)")
    void inactiveClassroomInActiveBuilding_isHidden() {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        classroom.deactivate();
        classroomRepository.save(classroom);

        assertThat(availableIds()).doesNotContain(classroom.getId());
        assertThat(classroomService.findByIds(List.of(classroom.getId()))).isEmpty();
    }

    @Test
    @DisplayName("buscar un aula por número y edificio inactivo no la devuelve")
    void findByRoomNumberAndInactiveBuilding_isNotFound() {
        Building building = testData.edificio("Edificio-Ing", false);
        Classroom classroom = testData.aula(building);

        assertThatThrownBy(() -> classroomService.findByRoomNumberAndBuilding(
                classroom.getRoomNumber(), building.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("buscar un aula por número y código de un edificio inactivo devuelve vacío")
    void findByRoomNumberAndInactiveBuildingCode_isEmpty() {
        Building inactive = Building.builder()
                .name("Edificio-Cod-" + IntegrationTestData.nextSeq())
                .buildingCode(90_000 + (int) (IntegrationTestData.nextSeq() % 9_000))
                .build();
        inactive.deactivate();
        Building building = buildingRepository.save(inactive);
        Classroom classroom = testData.aula(building);

        assertThat(classroomService.findByRoomNumberAndBuildingCode(
                classroom.getRoomNumber(), building.getBuildingCode())).isEmpty();
    }

    @Test
    @DisplayName("buscar un edificio inactivo por nombre no lo devuelve")
    void findInactiveBuildingByName_isNotFound() {
        Building building = testData.edificio("Edificio-Nombre", false);

        assertThatThrownBy(() -> buildingService.findByName(building.getName()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("el listado de un aula omite los recursos cuyo tipo de recurso está inhabilitado")
    void classroomListOmitsResourcesOfInactiveResourceType() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        ResourceType kept = testData.tipoRecurso("Proyector-" + IntegrationTestData.nextSeq(), ResourceValueKind.COUNT);
        ResourceType dropped = testData.tipoRecurso("Pizarra-" + IntegrationTestData.nextSeq(), ResourceValueKind.COUNT);
        testData.recursoDeAula(classroom, kept, 1);
        testData.recursoDeAula(classroom, dropped, 1);
        dropped.deactivate();
        resourceTypeRepository.save(dropped);

        mockMvc.perform(get("/v1/classrooms").param("buildingId", String.valueOf(building.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resources[*].name", hasItem(kept.getName())))
                .andExpect(jsonPath("$.content[0].resources[*].name", not(hasItem(dropped.getName()))));
    }
}
