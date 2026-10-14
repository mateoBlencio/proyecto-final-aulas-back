package ar.edu.utn.frc.siga.roomrequest.dto.response;

/** {@code availableComputerCount}: suma de PCs de las aulas libres; {@code null} si el pedido no pide PCs. */
public record CandidateBuildingDto(Long id, String name, int availableClassroomCount,
                                   Integer availableComputerCount) {}
