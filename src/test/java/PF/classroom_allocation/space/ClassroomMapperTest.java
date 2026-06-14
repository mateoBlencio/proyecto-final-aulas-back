package PF.classroom_allocation.space;

import PF.classroom_allocation.space.dto.ClassroomRequestDTO;
import PF.classroom_allocation.space.dto.ClassroomResponseDTO;
import PF.classroom_allocation.space.mapper.ClassroomMapper;
import PF.classroom_allocation.space.model.Building;
import PF.classroom_allocation.space.model.Classroom;
import PF.classroom_allocation.space.model.ClassroomType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomMapperTest {

    private final ClassroomMapper mapper = new ClassroomMapper();

    @Test
    void toResponseDto_shouldMapAllFields() {
        Building building = Building.builder().id(1).name("Edificio A").build();

        ClassroomType type = new ClassroomType();
        type.setId(2);
        type.setDescription("LABORATORIO");

        Classroom entity = new Classroom();
        entity.setId(10);
        entity.setRoomNumber("101");
        entity.setCapacity(30);
        entity.setFloor(2);
        entity.setAvailable(true);
        entity.setDeleted(false);
        entity.setBuilding(building);
        entity.setClassroomType(type);

        ClassroomResponseDTO dto = mapper.toResponseDto(entity);

        assertEquals(10, dto.getId());
        assertEquals("101", dto.getRoomNumber());
        assertEquals(30, dto.getCapacity());
        assertEquals(2, dto.getFloor());
        assertTrue(dto.getAvailable());
        assertEquals(1, dto.getBuildingId());
        assertEquals("Edificio A", dto.getBuildingName());
        assertEquals(2, dto.getClassroomTypeId());
        assertEquals("LABORATORIO", dto.getClassroomTypeDescription());
    }

    @Test
    void toResponseDto_shouldHandleNullBuilding() {
        Classroom entity = new Classroom();
        entity.setId(1);
        entity.setRoomNumber("101");
        entity.setCapacity(30);
        entity.setFloor(1);
        entity.setAvailable(true);
        entity.setBuilding(null);
        entity.setClassroomType(new ClassroomType());

        ClassroomResponseDTO dto = mapper.toResponseDto(entity);

        assertNull(dto.getBuildingId());
        assertNull(dto.getBuildingName());
    }

    @Test
    void toResponseDto_shouldHandleNullClassroomType() {
        Building building = Building.builder().id(1).name("Edificio A").build();
        Classroom entity = new Classroom();
        entity.setId(1);
        entity.setRoomNumber("101");
        entity.setCapacity(30);
        entity.setFloor(1);
        entity.setAvailable(true);
        entity.setBuilding(building);
        entity.setClassroomType(null);

        ClassroomResponseDTO dto = mapper.toResponseDto(entity);

        assertNull(dto.getClassroomTypeId());
        assertNull(dto.getClassroomTypeDescription());
    }

    @Test
    void toEntity_shouldMapFields() {
        var dto = new ClassroomRequestDTO("202", 25, 3, 1, true, 2);

        Classroom entity = mapper.toEntity(dto);

        assertEquals("202", entity.getRoomNumber());
        assertEquals(25, entity.getCapacity());
        assertEquals(3, entity.getFloor());
        assertTrue(entity.getAvailable());
    }

    @Test
    void updateEntity_shouldUpdateFields() {
        Classroom entity = new Classroom();
        entity.setRoomNumber("OLD");
        entity.setCapacity(10);
        entity.setFloor(1);
        entity.setAvailable(false);

        var dto = new ClassroomRequestDTO("NEW", 99, 5, 1, true, 2);
        mapper.updateEntity(entity, dto);

        assertEquals("NEW", entity.getRoomNumber());
        assertEquals(99, entity.getCapacity());
        assertEquals(5, entity.getFloor());
        assertTrue(entity.getAvailable());
    }
}
