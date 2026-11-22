package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSysacadRefDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("ClassroomService.findSysacadRefs (integración)")
class ClassroomFindSysacadRefsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private ClassroomRepository classroomRepository;

    private Building buildingWithCode(Integer code) {
        Building building = testData.edificio();
        building.setBuildingCode(code);
        return buildingRepository.save(building);
    }

    private static int uniqueCode() {
        return 20_000 + (int) (IntegrationTestData.nextSeq() % 1_000_000);
    }

    @Test
    @DisplayName("Devuelve número, código de edificio y vigencia de cada aula pedida, y omite ids inexistentes")
    void returnsRoomNumberBuildingCodeAndCurrency() {
        int code = uniqueCode();
        Building building = buildingWithCode(code);
        Classroom first = testData.aulaConNumero(228, building, testData.tipoAulaPorDefecto(), 40);
        Classroom second = testData.aulaConNumero(229, building, testData.tipoAulaPorDefecto(), 40);
        long unknownId = 900_000_000L + IntegrationTestData.nextSeq();

        List<ClassroomSysacadRefDto> refs = classroomService.findSysacadRefs(
                List.of(first.getId(), second.getId(), unknownId));

        assertThat(refs).containsExactlyInAnyOrder(
                new ClassroomSysacadRefDto(first.getId(), 228, code, true, true, true),
                new ClassroomSysacadRefDto(second.getId(), 229, code, true, true, true));
    }

    @Test
    @DisplayName("Un aula desactivada se devuelve igual, con su número y su edificio")
    void includesDeactivatedClassroom() {
        int code = uniqueCode();
        Classroom classroom = testData.aulaConNumero(230, buildingWithCode(code), testData.tipoAulaPorDefecto(), 40);
        classroom.deactivate();
        classroomRepository.save(classroom);

        List<ClassroomSysacadRefDto> refs = classroomService.findSysacadRefs(List.of(classroom.getId()));

        assertThat(refs).containsExactly(new ClassroomSysacadRefDto(classroom.getId(), 230, code, true, true, true));
    }

    @Test
    @DisplayName("Un aula que SysAcad ya no lista viene con currentInSysacad en falso")
    void classroomDroppedFromSysacadIsNotCurrent() {
        int code = uniqueCode();
        Classroom classroom = testData.aulaConNumero(231, buildingWithCode(code), testData.tipoAulaPorDefecto(), 40);
        classroom.setSysacadHash("h-" + classroom.getRoomNumber());
        classroom.setSysacadCurrent(false);
        classroom.setSysacadEnabled(true);
        classroomRepository.save(classroom);

        List<ClassroomSysacadRefDto> refs = classroomService.findSysacadRefs(List.of(classroom.getId()));

        assertThat(refs).containsExactly(new ClassroomSysacadRefDto(classroom.getId(), 231, code, false, true, true));
    }

    @Test
    @DisplayName("Un aula con habilitada = N en SysAcad viene con enabledInSysacad en falso y sigue vigente")
    void classroomDisabledInSysacadIsNotEnabled() {
        int code = uniqueCode();
        Classroom classroom = testData.aulaConNumero(232, buildingWithCode(code), testData.tipoAulaPorDefecto(), 40);
        classroom.setSysacadHash("h-" + classroom.getRoomNumber());
        classroom.setSysacadCurrent(true);
        classroom.setSysacadEnabled(false);
        classroomRepository.save(classroom);

        List<ClassroomSysacadRefDto> refs = classroomService.findSysacadRefs(List.of(classroom.getId()));

        assertThat(refs).containsExactly(new ClassroomSysacadRefDto(classroom.getId(), 232, code, true, false, true));
    }

    @Test
    @DisplayName("Un aula de un edificio que SysAcad ya no lista viene con buildingCurrentInSysacad en falso")
    void buildingDroppedFromSysacadIsNotCurrent() {
        int code = uniqueCode();
        Building building = buildingWithCode(code);
        building.setSysacadHash("h-" + code);
        building.setSysacadEnabled(false);
        buildingRepository.save(building);
        Classroom classroom = testData.aulaConNumero(233, building, testData.tipoAulaPorDefecto(), 40);

        List<ClassroomSysacadRefDto> refs = classroomService.findSysacadRefs(List.of(classroom.getId()));

        assertThat(refs).containsExactly(new ClassroomSysacadRefDto(classroom.getId(), 233, code, true, true, false));
    }

    @Test
    @DisplayName("Un edificio sin código de SysAcad se devuelve con buildingCode nulo")
    void buildingWithoutCodeHasNullCode() {
        Classroom classroom = testData.aulaConNumero(232, buildingWithCode(null), testData.tipoAulaPorDefecto(), 40);

        List<ClassroomSysacadRefDto> refs = classroomService.findSysacadRefs(List.of(classroom.getId()));

        assertThat(refs).containsExactly(new ClassroomSysacadRefDto(classroom.getId(), 232, null, true, true, true));
    }

    @Test
    @DisplayName("Sin ids devuelve lista vacía")
    void emptyIdsReturnEmptyList() {
        assertThat(classroomService.findSysacadRefs(List.of())).isEmpty();
    }
}
