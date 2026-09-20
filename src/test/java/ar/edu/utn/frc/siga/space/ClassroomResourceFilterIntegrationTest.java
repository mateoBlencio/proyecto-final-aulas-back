package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(IntegrationTestData.class)
@DisplayName("ClassroomService.findIdsWithResourceAtLeast (integración)")
class ClassroomResourceFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private ClassroomService classroomService;

    @Test
    @DisplayName("cantidad = 0 en 'Cantidad de PC' no aparece al pedir >= 1")
    void classroomWithZeroQuantity_doesNotAppear() {
        Building building = testData.edificio();
        ResourceType pcs = testData.tipoRecurso("Cantidad de PC " + IntegrationTestData.nextSeq(), ResourceValueKind.COUNT);
        Classroom classroom = testData.aula(building);
        testData.recursoDeAula(classroom, pcs, 0);

        Set<Long> result = classroomService.findIdsWithResourceAtLeast(pcs.getName(), 1);

        assertThat(result).doesNotContain(classroom.getId());
    }

    @Test
    @DisplayName("cantidad = 22 aparece al pedir >= 20 y no aparece al pedir >= 30")
    void classroomWithQuantity22_appearsOnlyBelowThreshold() {
        Building building = testData.edificio();
        ResourceType pcs = testData.tipoRecurso("Cantidad de PC " + IntegrationTestData.nextSeq(), ResourceValueKind.COUNT);
        Classroom classroom = testData.aula(building);
        testData.recursoDeAula(classroom, pcs, 22);

        assertThat(classroomService.findIdsWithResourceAtLeast(pcs.getName(), 20)).contains(classroom.getId());
        assertThat(classroomService.findIdsWithResourceAtLeast(pcs.getName(), 30)).doesNotContain(classroom.getId());
    }

    @Test
    @DisplayName("filtro por recurso booleano: solo las aulas con esa fila en aula_recurso")
    void booleanResource_onlyClassroomsWithRow() {
        Building building = testData.edificio();
        ResourceType projector = testData.tipoRecurso("Proyector " + IntegrationTestData.nextSeq(), ResourceValueKind.BOOLEAN);
        Classroom withProjector = testData.aula(building);
        Classroom withoutProjector = testData.aula(building);
        testData.recursoDeAula(withProjector, projector, 1);

        Set<Long> result = classroomService.findIdsWithResourceAtLeast(projector.getName(), 1);

        assertThat(result).contains(withProjector.getId()).doesNotContain(withoutProjector.getId());
    }

    @Test
    @DisplayName("aula sin ninguna fila de recurso: no aparece en ningún filtro")
    void classroomWithoutAnyResource_neverAppears() {
        Building building = testData.edificio();
        ResourceType pcs = testData.tipoRecurso("Cantidad de PC " + IntegrationTestData.nextSeq(), ResourceValueKind.COUNT);
        ResourceType projector = testData.tipoRecurso("Proyector " + IntegrationTestData.nextSeq(), ResourceValueKind.BOOLEAN);
        Classroom classroom = testData.aula(building);

        assertThat(classroomService.findIdsWithResourceAtLeast(pcs.getName(), 1)).doesNotContain(classroom.getId());
        assertThat(classroomService.findIdsWithResourceAtLeast(projector.getName(), 1)).doesNotContain(classroom.getId());
    }
}
