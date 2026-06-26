package ar.edu.utn.frc.classroom_allocation.space.repository;

import ar.edu.utn.frc.classroom_allocation.space.model.Building;
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
class BuildingRepositoryIntegrationTest {

    @Autowired
    private BuildingRepository buildingRepository;

    @Test
    void findByNameAndDeletedFalse_shouldReturnBuildingWhenExists() {
        Optional<Building> result = buildingRepository.findByNameAndDeletedFalse("Central");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Central");
    }

    @Test
    void findByNameAndDeletedFalse_shouldReturnEmptyWhenNameDoesNotMatch() {
        Optional<Building> result = buildingRepository.findByNameAndDeletedFalse("Inexistente");
        assertThat(result).isEmpty();
    }

    @Test
    void findByNameAndDeletedFalse_shouldReturnEmptyWhenBuildingIsDeleted() {
        Optional<Building> result = buildingRepository.findByNameAndDeletedFalse("Anexo");
        assertThat(result).isEmpty();
    }
}
