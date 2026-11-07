package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomSysacadRefDto(
        Long classroomId,
        Integer roomNumber,
        Integer buildingCode,
        boolean currentInSysacad) {
}
