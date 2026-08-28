package ar.edu.utn.frc.siga.sysacad.api;

public record SysacadClassroomDto(
        Integer roomNumber,
        Integer buildingCode,
        boolean isEnabled,
        Integer capacity
) {}
