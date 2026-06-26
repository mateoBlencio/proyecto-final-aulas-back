package ar.edu.utn.frc.classroom_allocation.space.repository;

import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("integration")
@Sql(scripts = "/space/integration/setup-classroom.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/space/integration/cleanup.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClassroomRepositoryIntegrationTest {

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Test
    void findByRoomNumberAndBuildingAndDeletedFalse_shouldReturnClassroomWhenExists() {
        Building building = buildingRepository.findByIdAndDeletedFalse(1).orElseThrow();
        Optional<Classroom> result = classroomRepository
            .findByRoomNumberAndBuildingAndDeletedFalse("101", building);
        assertThat(result).isPresent();
        assertThat(result.get().getRoomNumber()).isEqualTo("101");
    }

    @Test
    void findByRoomNumberAndBuildingAndDeletedFalse_shouldReturnEmptyWhenRoomNumberDoesNotMatch() {
        Building building = buildingRepository.findByIdAndDeletedFalse(1).orElseThrow();
        Optional<Classroom> result = classroomRepository
            .findByRoomNumberAndBuildingAndDeletedFalse("999", building);
        assertThat(result).isEmpty();
    }

    @Test
    void findByRoomNumberAndBuildingAndDeletedFalse_shouldReturnEmptyWhenBuildingDoesNotMatch() {
        Building building1 = buildingRepository.findByIdAndDeletedFalse(1).orElseThrow();
        Building building2 = buildingRepository.findByIdAndDeletedFalse(2).orElseThrow();
        // "101" exists in building 1, not in building 2
        Optional<Classroom> result = classroomRepository
            .findByRoomNumberAndBuildingAndDeletedFalse("101", building2);
        assertThat(result).isEmpty();
    }

    @Test
    void findByRoomNumberAndBuildingAndDeletedFalse_shouldReturnEmptyWhenClassroomIsDeleted() {
        // "201" in building 2 (Possetto) has eliminado=true
        Building building = buildingRepository.findByIdAndDeletedFalse(2)
            .orElseThrow(() -> new AssertionError("Building 2 should exist"));
        Optional<Classroom> result = classroomRepository
            .findByRoomNumberAndBuildingAndDeletedFalse("201", building);
        assertThat(result).isEmpty();
    }
}
