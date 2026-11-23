package ar.edu.utn.frc.siga.auth.dto.response;

import java.util.List;

public record PermissionGrantDto(String permission, boolean allBuildings, List<Long> buildingIds) {
}
