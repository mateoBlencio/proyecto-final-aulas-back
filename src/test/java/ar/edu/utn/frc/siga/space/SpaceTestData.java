package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;

/**
 * Fixture única de datos de prueba del módulo {@code space}. Métodos estáticos que arman
 * entidades/DTOs con valores por defecto razonables, sobreescribibles vía el builder de Lombok.
 */
public class SpaceTestData {

    public static Building.BuildingBuilder building() {
        return Building.builder()
                .id(1)
                .name("Edificio Central")
                .floorCount(5)
                .active(true)
                .deleted(false);
    }

    public static ClassroomType.ClassroomTypeBuilder classroomType() {
        return ClassroomType.builder()
                .id(1)
                .description("Normal")
                .deleted(false);
    }

    public static Classroom.ClassroomBuilder classroom() {
        return Classroom.builder()
                .id(1)
                .roomNumber("101")
                .floor(1)
                .capacity(40)
                .available(true)
                .deleted(false)
                .building(building().build())
                .classroomType(classroomType().build());
    }

    public static ClassroomRequestDto classroomRequestDto() {
        return new ClassroomRequestDto("101", 40, 1, 1, true, 1);
    }

    public static ClassroomRequestDto classroomRequestDto(String roomNumber, Integer capacity, Integer floor,
                                                            Integer classroomTypeId, Boolean available, Integer buildingId) {
        return new ClassroomRequestDto(roomNumber, capacity, floor, classroomTypeId, available, buildingId);
    }
}
