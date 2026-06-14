package PF.classroom_allocation.space;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.dto.ClassroomRequestDTO;
import PF.classroom_allocation.space.dto.ClassroomResponseDTO;
import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.exception.SpaceDomainException;
import PF.classroom_allocation.space.mapper.ClassroomMapper;
import PF.classroom_allocation.space.model.Building;
import PF.classroom_allocation.space.model.Classroom;
import PF.classroom_allocation.space.model.ClassroomType;
import PF.classroom_allocation.space.repository.ClassroomRepository;
import PF.classroom_allocation.space.service.BuildingService;
import PF.classroom_allocation.space.service.ClassroomTypeService;
import PF.classroom_allocation.space.service.impl.ClassroomServiceImpl;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private BuildingService buildingService;

    @Mock
    private ClassroomTypeService classroomTypeService;

    private ClassroomMapper classroomMapper;

    private ClassroomServiceImpl classroomService;

    private Building building;
    private ClassroomType classroomType;
    private ClassroomRequestDTO validDto;
    private Classroom existingClassroom;
    private Classroom inactiveClassroom;

    @BeforeEach
    void setUp() {
        classroomMapper = new ClassroomMapper();
        classroomService = new ClassroomServiceImpl(classroomRepository, buildingService, classroomTypeService, classroomMapper);

        building = Building.builder()
                .name("Pabellon A")
                .floorCount(5)
                .id(1)
                .build();

        classroomType = new ClassroomType();
        classroomType.setId(1);
        classroomType.setDescription("CLASSROOM");

        validDto = new ClassroomRequestDTO("101", 30, 2, 1, true, 1);

        existingClassroom = new Classroom();
        existingClassroom.setId(1);
        existingClassroom.setRoomNumber("101");
        existingClassroom.setCapacity(30);
        existingClassroom.setFloor(2);
        existingClassroom.setClassroomType(classroomType);
        existingClassroom.setAvailable(true);
        existingClassroom.setDeleted(false);
        existingClassroom.setBuilding(building);

        inactiveClassroom = new Classroom();
        inactiveClassroom.setId(2);
        inactiveClassroom.setRoomNumber("102");
        inactiveClassroom.setCapacity(20);
        inactiveClassroom.setFloor(1);
        inactiveClassroom.setClassroomType(classroomType);
        inactiveClassroom.setAvailable(false);
        inactiveClassroom.setDeleted(true);
        inactiveClassroom.setBuilding(building);
    }

    @Test
    void create_shouldReturnResponseDtoWhenValid() {
        when(buildingService.findById(validDto.buildingId())).thenReturn(building);
        when(classroomTypeService.findById(validDto.classroomTypeId())).thenReturn(classroomType);
        when(classroomRepository.findByRoomNumberAndDeletedFalse("101")).thenReturn(Optional.empty());

        Classroom saved = new Classroom();
        saved.setId(1);
        saved.setRoomNumber("101");
        saved.setCapacity(30);
        saved.setFloor(2);
        saved.setClassroomType(classroomType);
        saved.setAvailable(true);
        saved.setDeleted(false);
        saved.setBuilding(building);

        when(classroomRepository.save(any(Classroom.class))).thenReturn(saved);

        ClassroomResponseDTO result = classroomService.create(validDto);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("101", result.getRoomNumber());
        assertEquals(30, result.getCapacity());
        assertEquals(2, result.getFloor());
        assertTrue(result.getAvailable());
        assertEquals(building.getId(), result.getBuildingId());
        assertEquals(building.getName(), result.getBuildingName());
        assertEquals(classroomType.getId(), result.getClassroomTypeId());
        assertEquals(classroomType.getDescription(), result.getClassroomTypeDescription());
    }

    @Test
    void create_shouldThrowWhenBuildingNotFound() {
        when(buildingService.findById(validDto.buildingId()))
                .thenThrow(new ResourceNotFoundException("Building not found"));

        assertThrows(ResourceNotFoundException.class, () -> classroomService.create(validDto));
    }

    @Test
    void create_shouldThrowWhenRoomNumberDuplicated() {
        when(buildingService.findById(validDto.buildingId())).thenReturn(building);
        when(classroomTypeService.findById(validDto.classroomTypeId())).thenReturn(classroomType);
        when(classroomRepository.findByRoomNumberAndDeletedFalse("101")).thenReturn(Optional.of(existingClassroom));

        assertThrows(SpaceDomainException.class, () -> classroomService.create(validDto));
    }

    @Test
    void create_shouldThrowWhenFloorExceedsBuilding() {
        var dto = new ClassroomRequestDTO("101", 30, 6, 1, true, 1);
        when(buildingService.findById(dto.buildingId())).thenReturn(building);
        when(classroomTypeService.findById(dto.classroomTypeId())).thenReturn(classroomType);
        when(classroomRepository.findByRoomNumberAndDeletedFalse("101")).thenReturn(Optional.empty());

        assertThrows(SpaceDomainException.class, () -> classroomService.create(dto));
    }

    @Test
    void create_shouldThrowWhenCapacityNotPositive() {
        var dto = new ClassroomRequestDTO("101", 0, 2, 1, true, 1);
        when(buildingService.findById(dto.buildingId())).thenReturn(building);
        when(classroomTypeService.findById(dto.classroomTypeId())).thenReturn(classroomType);
        when(classroomRepository.findByRoomNumberAndDeletedFalse("101")).thenReturn(Optional.empty());

        assertThrows(SpaceDomainException.class, () -> classroomService.create(dto));
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(classroomRepository.findByIdAndDeletedFalse(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> classroomService.findById(999));
    }

    @Test
    void findById_shouldThrowWhenInactive() {
        when(classroomRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> classroomService.findById(2));
    }

    @Test
    void findById_shouldReturnDtoWhenActive() {
        when(classroomRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(existingClassroom));

        ClassroomResponseDTO result = classroomService.findById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals(building.getId(), result.getBuildingId());
    }

    @Test
    void findAll_shouldReturnPageWhenActive() {
        ClassroomFilter filter = new ClassroomFilter(null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("id"));
        Page<Classroom> page = new PageImpl<>(List.of(existingClassroom), pageable, 1);

        when(classroomRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ClassroomResponseDTO> result = classroomService.findAll(filter, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("101", result.getContent().get(0).getRoomNumber());
    }

    @Test
    void findAll_shouldApplyFilter() {
        ClassroomFilter filter = new ClassroomFilter("101", 1, 1, 20, 50, 2, true);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Classroom> page = new PageImpl<>(List.of(existingClassroom), pageable, 1);

        when(classroomRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ClassroomResponseDTO> result = classroomService.findAll(filter, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void update_shouldSucceedWhenValid() {
        var newBuilding = Building.builder()
                .name("Pabellon B")
                .floorCount(10)
                .id(2)
                .build();

        var updateDto = new ClassroomRequestDTO("101", 40, 5, 1, false, 2);

        when(classroomRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(existingClassroom));
        when(buildingService.findById(2)).thenReturn(newBuilding);
        when(classroomTypeService.findById(1)).thenReturn(classroomType);

        Classroom updated = new Classroom();
        updated.setId(1);
        updated.setRoomNumber("101");
        updated.setCapacity(40);
        updated.setFloor(5);
        updated.setClassroomType(classroomType);
        updated.setAvailable(false);
        updated.setDeleted(false);
        updated.setBuilding(newBuilding);

        when(classroomRepository.save(any(Classroom.class))).thenReturn(updated);

        ClassroomResponseDTO result = classroomService.update(1, updateDto);

        assertNotNull(result);
        assertEquals(40, result.getCapacity());
        assertEquals(5, result.getFloor());
        assertFalse(result.getAvailable());
        assertEquals(2, result.getBuildingId());
    }

    @Test
    void update_shouldThrowWhenClassroomInactive() {
        var updateDto = new ClassroomRequestDTO("102", 20, 1, 1, false, building.getId());

        when(classroomRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> classroomService.update(2, updateDto));
    }

    @Test
    void update_shouldThrowWhenFloorExceedsNewBuilding() {
        var smallBuilding = Building.builder()
                .name("Small")
                .floorCount(1)
                .id(3)
                .build();

        var updateDto = new ClassroomRequestDTO("101", 30, 3, 1, true, 3);

        when(classroomRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(existingClassroom));
        when(buildingService.findById(3)).thenReturn(smallBuilding);
        when(classroomTypeService.findById(1)).thenReturn(classroomType);

        assertThrows(SpaceDomainException.class, () -> classroomService.update(1, updateDto));
    }

    @Test
    void delete_shouldSetDeletedTrueWhenExists() {
        when(classroomRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(existingClassroom));
        when(classroomRepository.save(any(Classroom.class))).thenReturn(existingClassroom);

        classroomService.delete(1);

        assertTrue(existingClassroom.getDeleted());
        verify(classroomRepository).save(existingClassroom);
        verify(classroomRepository, never()).deleteById(1);
    }

    @Test
    void delete_shouldThrowWhenClassroomInactive() {
        when(classroomRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> classroomService.delete(2));
    }

}
