package PF.classroom_allocation.space.service;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.dto.request.ClassroomRequestDTO;
import PF.classroom_allocation.space.dto.response.ClassroomResponseDTO;
import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.exception.SpaceDomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/space/integration/setup-classroom.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/space/integration/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClassroomServiceIntegrationTest {

    @Autowired
    private ClassroomService classroomService;

    @Test
    void create_shouldPersistEntityWithRelationships() {
        var dto = new ClassroomRequestDTO("300", 50, 3, 1, true, 1);

        ClassroomResponseDTO result = classroomService.create(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getRoomNumber()).isEqualTo("300");
        assertThat(result.getCapacity()).isEqualTo(50);
        assertThat(result.getFloor()).isEqualTo(3);
        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getBuildingId()).isEqualTo(1);
        assertThat(result.getBuildingName()).isEqualTo("Central");
        assertThat(result.getClassroomTypeId()).isEqualTo(1);
        assertThat(result.getClassroomTypeDescription()).isEqualTo("aula");
    }

    @Test
    void create_shouldThrowWhenDuplicateRoomNumber() {
        var dto = new ClassroomRequestDTO("101", 30, 2, 1, true, 1);

        assertThatThrownBy(() -> classroomService.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_shouldThrowWhenFloorExceedsBuilding() {
        var dto = new ClassroomRequestDTO("500", 30, 10, 1, true, 1);

        assertThatThrownBy(() -> classroomService.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void findAll_shouldFilterByAllFields() {
        var filter = new ClassroomFilter("101", 1, 1, 20, 50, 2, true);
        var pageable = PageRequest.of(0, 10);

        Page<ClassroomResponseDTO> result = classroomService.findAll(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getRoomNumber()).isEqualTo("101");
    }

    @Test
    void delete_shouldSetDeletedFlag() {
        classroomService.delete(1);

        assertThatThrownBy(() -> classroomService.findById(1))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
