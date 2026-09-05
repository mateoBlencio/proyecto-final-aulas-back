package ar.edu.utn.frc.siga.space.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@DisplayName("Repositorios de características de aula (JPA)")
class ClassroomFeatureRepositoriesTest {

    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private ClassroomTypeRepository classroomTypeRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private ResourceTypeRepository resourceTypeRepository;
    @Autowired
    private ClassroomResourceRepository classroomResourceRepository;
    @Autowired
    private ClassroomPermissionRepository classroomPermissionRepository;

    private Classroom classroom;

    @BeforeEach
    void seedClassroom() {
        Building building = buildingRepository.save(Building.builder().name("Edificio Test " + System.nanoTime()).build());
        ClassroomType type = classroomTypeRepository.save(
                ClassroomType.builder().description("Tipo Test " + System.nanoTime()).build());
        classroom = classroomRepository.save(Classroom.builder()
                .roomNumber((int) (System.nanoTime() % 100_000))
                .capacity(40)
                .building(building)
                .classroomType(type)
                .build());
    }

    @Test
    @DisplayName("Classroom persiste modo_permiso con default ALL y observaciones")
    void classroomPersistsPermissionModeAndObservations() {
        assertThat(classroom.getPermissionMode()).isEqualTo(PermissionMode.ALL);

        classroom.setPermissionMode(PermissionMode.SUBSET);
        classroom.setObservations("Sin ventanas");
        Classroom reloaded = classroomRepository.saveAndFlush(classroom);

        assertThat(reloaded.getPermissionMode()).isEqualTo(PermissionMode.SUBSET);
        assertThat(reloaded.getObservations()).isEqualTo("Sin ventanas");
    }

    @Test
    @DisplayName("ClassroomResource: consulta batch por aula y respeta el soft-delete")
    void classroomResourceBatchQueryRespectsSoftDelete() {
        ResourceType pc = resourceTypeRepository.save(ResourceType.builder()
                .name("Cantidad de PC " + System.nanoTime()).valueKind(ResourceValueKind.COUNT).build());
        ResourceType projector = resourceTypeRepository.save(ResourceType.builder()
                .name("Proyector " + System.nanoTime()).valueKind(ResourceValueKind.BOOLEAN).build());

        classroomResourceRepository.save(ClassroomResource.builder()
                .classroom(classroom).resourceType(pc).quantity(30).build());
        ClassroomResource removed = classroomResourceRepository.save(ClassroomResource.builder()
                .classroom(classroom).resourceType(projector).quantity(1).build());
        classroomResourceRepository.softDelete(removed);

        List<ClassroomResource> active =
                classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(List.of(classroom.getId()));

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getQuantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("ClassroomPermission: consulta batch por aula y respeta el soft-delete")
    void classroomPermissionBatchQueryRespectsSoftDelete() {
        classroomPermissionRepository.save(ClassroomPermission.builder()
                .classroom(classroom).targetKind(PermissionTargetKind.SUBJECT).targetId(7L).build());
        ClassroomPermission removed = classroomPermissionRepository.save(ClassroomPermission.builder()
                .classroom(classroom).targetKind(PermissionTargetKind.SUBJECT).targetId(8L).build());
        classroomPermissionRepository.softDelete(removed);

        List<ClassroomPermission> active =
                classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(List.of(classroom.getId()));

        assertThat(active).extracting(ClassroomPermission::getTargetId).containsExactly(7L);
    }

    @Test
    @DisplayName("ResourceTypeRepository: existsByNameIgnoreCase incluye soft-deleted")
    void resourceTypeExistsByName() {
        String name = "Aire acondicionado " + System.nanoTime();
        ResourceType saved = resourceTypeRepository.save(ResourceType.builder()
                .name(name).valueKind(ResourceValueKind.BOOLEAN).build());

        assertThat(resourceTypeRepository.existsByNameIgnoreCase(name.toLowerCase())).isTrue();
        assertThat(resourceTypeRepository.existsByNameIgnoreCaseAndIdNot(name, saved.getId())).isFalse();
        assertThat(resourceTypeRepository.existsByNameIgnoreCase("no existe " + System.nanoTime())).isFalse();

        resourceTypeRepository.softDelete(saved);
        assertThat(resourceTypeRepository.existsByNameIgnoreCase(name)).isTrue();
    }
}
