package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.common.security.ScopeType;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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

    private final List<Building> notCurrentBuildings = new ArrayList<>();
    private final List<Classroom> notCurrentClassrooms = new ArrayList<>();

    @AfterEach
    void restoreSysacadFlags() {
        notCurrentBuildings.forEach(building -> {
            Building fresh = buildingRepository.findById(building.getId()).orElseThrow();
            fresh.setSysacadEnabled(true);
            buildingRepository.save(fresh);
        });
        notCurrentClassrooms.forEach(classroom -> {
            Classroom fresh = classroomRepository.findById(classroom.getId()).orElseThrow();
            fresh.setSysacadEnabled(true);
            classroomRepository.save(fresh);
        });
        notCurrentBuildings.clear();
        notCurrentClassrooms.clear();
    }

    private Building markNotCurrent(Building building) {
        building.setSysacadEnabled(false);
        building.setSysacadHash("hash-" + building.getId());
        notCurrentBuildings.add(building);
        return buildingRepository.save(building);
    }

    private Classroom markNotCurrent(Classroom classroom) {
        classroom.setSysacadEnabled(false);
        classroom.setSysacadHash("hash-" + classroom.getId());
        notCurrentClassrooms.add(classroom);
        return classroomRepository.save(classroom);
    }

    private Classroom markCurrent(Classroom classroom) {
        Classroom fresh = classroomRepository.findById(classroom.getId()).orElseThrow();
        fresh.setSysacadEnabled(true);
        return classroomRepository.save(fresh);
    }

    private Building markCurrent(Building building) {
        Building fresh = buildingRepository.findById(building.getId()).orElseThrow();
        fresh.setSysacadEnabled(true);
        return buildingRepository.save(fresh);
    }

    private Building buildingWithCode() {
        long seq = IntegrationTestData.nextSeq();
        return buildingRepository.save(Building.builder()
                .name("Edificio-Cod-" + seq)
                .buildingCode(100_000 + (int) seq)
                .build());
    }

    private ResultActions listClassrooms(Building building, boolean includeDeactivated)
            throws Exception {
        return mockMvc.perform(get("/v1/classrooms")
                .param("buildingId", String.valueOf(building.getId()))
                .param("includeDeactivated", String.valueOf(includeDeactivated)));
    }

    private ResultActions listBuilding(Building building, boolean includeDeactivated)
            throws Exception {
        return mockMvc.perform(get("/v1/buildings")
                .param("name", building.getName())
                .param("includeDeactivated", String.valueOf(includeDeactivated)));
    }

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

    @Test
    @DisplayName("un aula no vigente en SysAcad se sigue ofreciendo y se lista con currentInSysacad false y sin motivo")
    void classroomNotInSysacad_isStillOfferedWithWarning() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long id = classroom.getId();

        markNotCurrent(classroom);

        assertThat(classroomRepository.findById(id).orElseThrow().isActive()).isTrue();
        assertThat(availableIds()).contains(id);
        assertThat(classroomService.findByIds(List.of(id))).hasSize(1);
        listClassrooms(building, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].unavailableReason", contains((Object) null)))
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].currentInSysacad", contains(false)));

        markCurrent(classroom);

        listClassrooms(building, false)
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].currentInSysacad", contains(true)));
    }

    @Test
    @DisplayName("notInSysacad=true devuelve solo el aula no vigente, no la manual con flag apagado ni la vigente")
    void notInSysacadFilter_returnsOnlyNotCurrentClassroom() throws Exception {
        Building building = testData.edificio();
        Classroom notCurrent = markNotCurrent(testData.aula(building));
        Classroom manual = testData.aula(building);
        manual.setSysacadEnabled(false);
        classroomRepository.save(manual);
        Classroom current = testData.aula(building);

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("notInSysacad", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(notCurrent.getId().intValue()))
                .andExpect(jsonPath("$.content[0].currentInSysacad").value(false))
                .andExpect(jsonPath("$.content[0].unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(manual.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(current.getId().intValue()))));
    }

    @Test
    @DisplayName("notInSysacad=true con includeDeactivated=true suma el aula no vigente dada de baja")
    void notInSysacadFilter_withIncludeDeactivated_addsDeactivatedNotCurrentClassroom() throws Exception {
        Building building = testData.edificio();
        Classroom notCurrent = markNotCurrent(testData.aula(building));
        Classroom deactivatedNotCurrent = markNotCurrent(testData.aula(building));
        deactivatedNotCurrent.deactivate();
        classroomRepository.save(deactivatedNotCurrent);
        testData.aula(building);

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("notInSysacad", "true"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(notCurrent.getId().intValue()));
        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("notInSysacad", "true")
                        .param("includeDeactivated", "true"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id",
                        hasItems(notCurrent.getId().intValue(), deactivatedNotCurrent.getId().intValue())));
    }

    @Test
    @DisplayName("notInSysacad=true: totalElements cuenta todas las coincidencias aunque size=1")
    void notInSysacadFilter_totalElementsIgnoresPageSize() throws Exception {
        Building building = testData.edificio();
        markNotCurrent(testData.aula(building));
        markNotCurrent(testData.aula(building));
        testData.aula(building);

        mockMvc.perform(get("/v1/classrooms")
                        .param("buildingId", String.valueOf(building.getId()))
                        .param("notInSysacad", "true")
                        .param("size", "1"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("notInSysacad=true: un AUXILIAR_AULICO con alcance sobre un edificio solo ve las aulas no vigentes de ese edificio")
    void notInSysacadFilter_isScopedToAuxiliarBuilding() throws Exception {
        Building own = testData.edificio();
        Building other = testData.edificio();
        Classroom ownNotCurrent = markNotCurrent(testData.aula(own));
        Classroom otherNotCurrent = markNotCurrent(testData.aula(other));
        testData.aula(own);
        MockMvc auxiliar = mockMvcAsScoped("aux-nosysacad-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, own.getId());

        auxiliar.perform(get("/v1/classrooms").param("notInSysacad", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownNotCurrent.getId().intValue()))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(otherNotCurrent.getId().intValue()))));
    }

    @Test
    @DisplayName("la resolución por número y edificio (ingest, sync de asignaciones) devuelve el aula no vigente y no el aula de un edificio no vigente")
    void roomNumberLookups_resolveNotCurrentClassroomButNotThoseOfNotCurrentBuilding() {
        Building building = buildingWithCode();
        Classroom notCurrent = markNotCurrent(testData.aula(building));
        Building hiddenBuilding = buildingWithCode();
        Classroom inHiddenBuilding = testData.aula(hiddenBuilding);
        markNotCurrent(hiddenBuilding);

        assertThat(classroomService.findByRoomNumberAndBuilding(notCurrent.getRoomNumber(), building.getId()).id())
                .isEqualTo(notCurrent.getId());
        assertThat(classroomService.findByRoomNumberAndBuildingCode(
                notCurrent.getRoomNumber(), building.getBuildingCode()))
                .hasValueSatisfying(dto -> assertThat(dto.id()).isEqualTo(notCurrent.getId()));
        assertThat(classroomService.findByRoomNumberAndBuildingCode(
                inHiddenBuilding.getRoomNumber(), hiddenBuilding.getBuildingCode())).isEmpty();
    }

    @Test
    @DisplayName("un aula cargada a mano (hash nulo, flag apagado) sigue apareciendo y sin motivo de no disponibilidad")
    void manualClassroom_staysVisible() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        classroom.setSysacadEnabled(false);
        classroomRepository.save(classroom);
        Long id = classroom.getId();

        assertThat(classroomRepository.findById(id).orElseThrow().getSysacadHash()).isNull();
        assertThat(availableIds()).contains(id);
        assertThat(classroomService.findByIds(List.of(id))).hasSize(1);
        listClassrooms(building, false)
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].unavailableReason", contains((Object) null)));
    }

    @Test
    @DisplayName("un edificio no vigente oculta a sus aulas (NOT_IN_SYSACAD) y reaparecen al volver a marcarlo vigente")
    void buildingNotInSysacad_hidesItsClassroomsAndTheyComeBack() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        Long id = classroom.getId();

        markNotCurrent(building);

        assertThat(availableIds()).doesNotContain(id);
        assertThat(classroomService.findByIds(List.of(id))).isEmpty();
        listClassrooms(building, false)
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id.intValue()))));
        listClassrooms(building, true)
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].unavailableReason",
                        contains(UnavailableReason.NOT_IN_SYSACAD.name())));

        markCurrent(building);

        assertThat(availableIds()).contains(id);
    }

    @Test
    @DisplayName("un edificio no vigente sale del listado de edificios, aparece con includeDeactivated y reaparece al volver a marcarlo vigente")
    void buildingNotInSysacad_isListedOnlyWithIncludeDeactivated() throws Exception {
        Building building = testData.edificio("Edificio-NoVigente", true);
        Long id = building.getId();
        listBuilding(building, false).andExpect(jsonPath("$.content[*].id", hasItem(id.intValue())));

        markNotCurrent(building);

        listBuilding(building, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id.intValue()))));
        listBuilding(building, true)
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].unavailableReason",
                        contains(UnavailableReason.NOT_IN_SYSACAD.name())));
        assertThatThrownBy(() -> buildingService.findByName(building.getName()))
                .isInstanceOf(ResourceNotFoundException.class);

        markCurrent(building);

        listBuilding(building, false).andExpect(jsonPath("$.content[*].id", hasItem(id.intValue())));
        assertThat(buildingService.findByName(building.getName()).id()).isEqualTo(id);
    }

    @Test
    @DisplayName("un edificio manual (hash nulo, flag apagado) sigue en el listado")
    void manualBuilding_staysListed() throws Exception {
        Building building = testData.edificio("Edificio-Manual", true);
        building.setSysacadEnabled(false);
        buildingRepository.save(building);

        listBuilding(building, false)
                .andExpect(jsonPath("$.content[*].id", hasItem(building.getId().intValue())))
                .andExpect(jsonPath("$.content[0].unavailableReason").doesNotExist());
    }

    @Test
    @DisplayName("el motivo en el DTO respeta la precedencia: baja propia, luego edificio dado de baja, luego no vigente")
    void unavailableReasonInDto_followsPrecedence() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = markNotCurrent(testData.aula(building));
        Long id = classroom.getId();
        String reasonPath = "$.content[?(@.id==" + id + ")].unavailableReason";

        building.deactivate();
        buildingRepository.save(building);
        listClassrooms(building, true)
                .andExpect(jsonPath(reasonPath, contains(UnavailableReason.PARENT_DEACTIVATED.name())));

        classroom = classroomRepository.findById(id).orElseThrow();
        classroom.deactivate();
        classroomRepository.save(classroom);
        listClassrooms(building, true)
                .andExpect(jsonPath(reasonPath, contains(UnavailableReason.DEACTIVATED.name())));
    }
}
