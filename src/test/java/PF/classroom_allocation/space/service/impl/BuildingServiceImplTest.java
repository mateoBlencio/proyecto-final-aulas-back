package PF.classroom_allocation.space.service.impl;

import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.model.Building;
import PF.classroom_allocation.space.repository.BuildingRepository;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuildingServiceImplTest {

    @Mock
    private BuildingRepository buildingRepository;

    private BuildingServiceImpl buildingService;

    private Building activeBuilding;
    private Building inactiveBuilding;

    @BeforeEach
    void setUp() {
        buildingService = new BuildingServiceImpl(buildingRepository);

        activeBuilding = Building.builder()
                .id(1)
                .name("Pabellon A")
                .floorCount(5)
                .active(true)
                .build();

        inactiveBuilding = Building.builder()
                .id(2)
                .name("Pabellon B")
                .floorCount(3)
                .active(false)
                .build();
    }

    @Test
    void findById_shouldReturnBuildingWhenActiveAndNotDeleted() {
        when(buildingRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(activeBuilding));

        Building result = buildingService.findById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("Pabellon A", result.getName());
    }

    @Test
    void findById_shouldThrowWhenBuildingExistsButIsInactive() {
        when(buildingRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.of(inactiveBuilding));

        var ex = assertThrows(ResourceNotFoundException.class, () -> buildingService.findById(2));
        assertTrue(ex.getMessage().contains("Building not found"));
    }

    @Test
    void findById_shouldThrowWhenBuildingNotFound() {
        when(buildingRepository.findByIdAndDeletedFalse(999)).thenReturn(Optional.empty());

        var ex = assertThrows(ResourceNotFoundException.class, () -> buildingService.findById(999));
        assertTrue(ex.getMessage().contains("Building not found"));
    }

    @Test
    void findById_shouldCallRepositoryWithCorrectId() {
        when(buildingRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(activeBuilding));

        buildingService.findById(1);

        verify(buildingRepository).findByIdAndDeletedFalse(1);
    }

}
