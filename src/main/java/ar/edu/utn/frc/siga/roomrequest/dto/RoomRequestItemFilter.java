package ar.edu.utn.frc.siga.roomrequest.dto;

import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

import java.time.LocalDate;
import java.util.Set;


public record RoomRequestItemFilter(
        Set<RoomRequestType> types,
        Set<RoomRequestStatus> statuses,
        AcademicScope scope,
        Long subjectId,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includePast,
        Boolean requiresSpecialAssignment,
        Long derivedBuildingId,
        Boolean partiallyResolved,
        Boolean wasReturned,
        Set<Long> restrictToBuildingIds) {

    public static RoomRequestItemFilter of(Set<RoomRequestType> types, Set<RoomRequestStatus> statuses,
            AcademicScope scope, Long subjectId, LocalDate dateFrom, LocalDate dateTo, boolean includePast,
            Boolean requiresSpecialAssignment, Long derivedBuildingId, Boolean partiallyResolved,
            Boolean wasReturned) {
        LocalDate effectiveFrom = includePast ? dateFrom : laterOf(dateFrom, LocalDate.now());
        if (effectiveFrom != null) {
            DateRanges.requireNotBefore(dateTo, effectiveFrom);
        }
        return new RoomRequestItemFilter(types, statuses, scope, subjectId, effectiveFrom, dateTo, includePast,
                requiresSpecialAssignment, derivedBuildingId, partiallyResolved, wasReturned, null);
    }

    /** Recorta el resultado a los edificios a cargo del auxiliar áulico que consulta; null = sin recorte. */
    public RoomRequestItemFilter restrictedToBuildings(Set<Long> buildingIds) {
        return new RoomRequestItemFilter(types, statuses, scope, subjectId, dateFrom, dateTo, includePast,
                requiresSpecialAssignment, derivedBuildingId, partiallyResolved, wasReturned, buildingIds);
    }

    private static LocalDate laterOf(LocalDate dateFrom, LocalDate today) {
        return dateFrom == null || dateFrom.isBefore(today) ? today : dateFrom;
    }
}
