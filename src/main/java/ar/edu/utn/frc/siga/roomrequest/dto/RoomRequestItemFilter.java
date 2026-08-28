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
        boolean includePast) {

    public static RoomRequestItemFilter of(Set<RoomRequestType> types, Set<RoomRequestStatus> statuses,
            AcademicScope scope, Long subjectId, LocalDate dateFrom, LocalDate dateTo, boolean includePast) {
        LocalDate effectiveFrom = includePast ? dateFrom : laterOf(dateFrom, LocalDate.now());
        if (effectiveFrom != null) {
            DateRanges.requireNotBefore(dateTo, effectiveFrom);
        }
        return new RoomRequestItemFilter(types, statuses, scope, subjectId, effectiveFrom, dateTo, includePast);
    }

    private static LocalDate laterOf(LocalDate dateFrom, LocalDate today) {
        return dateFrom == null || dateFrom.isBefore(today) ? today : dateFrom;
    }
}
