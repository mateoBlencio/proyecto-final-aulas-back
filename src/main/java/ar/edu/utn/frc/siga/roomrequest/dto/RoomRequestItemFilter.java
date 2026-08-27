package ar.edu.utn.frc.siga.roomrequest.dto;

import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

import java.time.LocalDate;
import java.util.Set;

/**
 * Filtros del listado de pedidos ({@code GET /v1/room-requests/items}). Usar siempre {@link #of}
 * en vez del constructor canónico: resuelve el rango de fechas efectivo antes de llegar a la
 * Specification.
 */
public record RoomRequestItemFilter(
        Set<RoomRequestType> types,
        Set<RoomRequestStatus> statuses,
        AcademicScope scope,
        Long subjectId,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includePast) {

    /**
     * Con {@code includePast=false} (el caso normal) el {@code dateFrom} efectivo es
     * {@code max(dateFrom, hoy)}, para que los pedidos ya vencidos no aparezcan en la bandeja.
     * Con {@code includePast=true} se respeta el {@code dateFrom} del cliente tal cual, null incluido
     * (sin piso). Valida {@code dateTo >= dateFrom} cuando ambos quedan definidos.
     */
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
