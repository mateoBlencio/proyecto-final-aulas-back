package ar.edu.utn.frc.siga.dashboard.dto.response;

public record DashboardBadgesResponseDto(
        long roomRequestsAwaitingAction,
        long roomRequestsExpiringSoon,
        Long unallocatedClassesNext7Days,
        long conflictsNext7Days) {
}
