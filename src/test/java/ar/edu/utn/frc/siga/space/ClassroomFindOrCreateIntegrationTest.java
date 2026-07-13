package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ClassroomService.findOrCreate} provisional: no hay endpoint HTTP (lo usa
 * {@code excelimport}), así que se inyecta el service directamente.
 */
@Import(IntegrationTestData.class)
@DisplayName("ClassroomService.findOrCreate (integración)")
class ClassroomFindOrCreateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ClassroomService classroomService;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Test
    @DisplayName("primera llamada crea aula provisional: floor 0, capacity=enrolled, tipo por defecto")
    void findOrCreate_createsProvisionalClassroom() {
        testData.tipoAulaNormal();
        Building building = testData.edificio();
        String roomNumber = "PROV-" + IntegrationTestData.nextSeq();

        FindOrCreateResult<ClassroomResponseDto> result = classroomService.findOrCreate(roomNumber, building.getId(), 25);

        assertThat(result.created()).isTrue();
        ClassroomResponseDto dto = result.value();
        assertThat(dto.floor()).isZero();
        assertThat(dto.capacity()).isEqualTo(25);
        assertThat(dto.classroomTypeDescription()).isEqualToIgnoringCase("Normal");
        assertThat(classroomRepository.findById(dto.id())).isPresent();
    }

    @Test
    @DisplayName("capacity cae a 1 cuando enrolledCount es null o <= 0")
    void findOrCreate_defaultsCapacityToOne_whenEnrolledMissingOrNonPositive() {
        testData.tipoAulaNormal();
        Building building = testData.edificio();

        FindOrCreateResult<ClassroomResponseDto> withNull =
                classroomService.findOrCreate("PROV-" + IntegrationTestData.nextSeq(), building.getId(), null);
        FindOrCreateResult<ClassroomResponseDto> withZero =
                classroomService.findOrCreate("PROV-" + IntegrationTestData.nextSeq(), building.getId(), 0);

        assertThat(withNull.value().capacity()).isEqualTo(1);
        assertThat(withZero.value().capacity()).isEqualTo(1);
    }

    @Test
    @DisplayName("segunda llamada idéntica reusa el aula: created=false, misma id, sin duplicado en BD")
    void findOrCreate_secondCallReusesExisting_withoutDuplicating() {
        testData.tipoAulaNormal();
        Building building = testData.edificio();
        String roomNumber = "PROV-" + IntegrationTestData.nextSeq();

        long countBefore = classroomRepository.count();

        FindOrCreateResult<ClassroomResponseDto> first = classroomService.findOrCreate(roomNumber, building.getId(), 25);
        long countAfterFirst = classroomRepository.count();

        FindOrCreateResult<ClassroomResponseDto> second = classroomService.findOrCreate(roomNumber, building.getId(), 99);
        long countAfterSecond = classroomRepository.count();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        // enrolledCount de la segunda llamada se ignora: el aula reusada no se pisa.
        assertThat(second.value().capacity()).isEqualTo(25);

        assertThat(countAfterFirst).isEqualTo(countBefore + 1);
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }
}
