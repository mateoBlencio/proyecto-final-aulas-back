package ar.edu.utn.frc.siga.roomrequest.dto.request;

import java.time.LocalTime;
import java.util.List;

/**
 * Reglas de coherencia de un pedido que no dependen del tipo de solicitud. Se comparten entre las
 * formas de ítem ({@link ScheduledItemDto}, {@link FreeFormItemDto}) para no duplicar los
 * {@code @AssertTrue}.
 */
final class RoomRequestItemChecks {

    private RoomRequestItemChecks() {
    }

    /** Rango válido si falta alguno de los extremos (esa ausencia la valida el tipo) o si el fin es posterior. */
    static boolean timeRangeValid(LocalTime startTime, LocalTime endTime) {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    /** Si-y-sólo-si: pide computadoras ⇔ indica cuántas. */
    static boolean computerCountConsistent(Boolean requiresComputers, Integer computerCount) {
        return Boolean.TRUE.equals(requiresComputers) == (computerCount != null);
    }

    /** Sólo se puede indicar software si además pide computadoras. */
    static boolean requiredSoftwareConsistent(Boolean requiresComputers, String requiredSoftware) {
        return requiredSoftware == null || Boolean.TRUE.equals(requiresComputers);
    }

    static boolean preferencesDistinct(List<Long> preferredClassroomIds) {
        return preferredClassroomIds == null
                || preferredClassroomIds.size() == preferredClassroomIds.stream().distinct().count();
    }
}
