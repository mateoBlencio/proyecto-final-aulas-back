package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDTO;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.springframework.stereotype.Component;

@Component
public class ClassroomMapper {

    public ClassroomResponseDTO toResponseDto(Classroom entity) {
        ClassroomResponseDTO.ClassroomResponseDTOBuilder builder = ClassroomResponseDTO.builder()
                .id(entity.getId())
                .roomNumber(entity.getRoomNumber())
                .capacity(entity.getCapacity())
                .floor(entity.getFloor())
                .available(entity.getAvailable());

        if (entity.getBuilding() != null) {
            builder.buildingId(entity.getBuilding().getId())
                    .buildingName(entity.getBuilding().getName());
        }

        if (entity.getClassroomType() != null) {
            builder.classroomTypeId(entity.getClassroomType().getId())
                    .classroomTypeDescription(entity.getClassroomType().getDescription());
        }

        return builder.build();
    }

    public Classroom toEntity(ClassroomRequestDTO dto) {
        return Classroom.builder()
                .roomNumber(dto.roomNumber())
                .capacity(dto.capacity())
                .floor(dto.floor())
                .available(dto.available())
                .build();
    }

    public void updateEntity(Classroom entity, ClassroomRequestDTO dto) {
        entity.setRoomNumber(dto.roomNumber());
        entity.setCapacity(dto.capacity());
        entity.setFloor(dto.floor());
        entity.setAvailable(dto.available());
    }

}
