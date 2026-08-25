package ar.edu.utn.frc.siga.space;

import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;

public class SpaceTestData {

    public static Building.BuildingBuilder building() {
        return Building.builder()
                .id(1L)
                .name("Edificio Central");
    }

    public static ClassroomType.ClassroomTypeBuilder classroomType() {
        return ClassroomType.builder()
                .id(1L)
                .description("Normal");
    }

    public static Classroom.ClassroomBuilder classroom() {
        return Classroom.builder()
                .id(1L)
                .roomNumber(101)
                .capacity(40)
                .building(building().build())
                .classroomType(classroomType().build());
    }

    public static ClassroomRequestDto classroomRequestDto() {
        return new ClassroomRequestDto(101, 40, 1L, 1L);
    }

    public static ClassroomRequestDto classroomRequestDto(Integer roomNumber, Integer capacity,
                                                            Long classroomTypeId, Long buildingId) {
        return new ClassroomRequestDto(roomNumber, capacity, classroomTypeId, buildingId);
    }
}
