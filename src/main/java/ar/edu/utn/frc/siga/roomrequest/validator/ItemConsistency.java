package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public final class ItemConsistency {

    public static final String COMMISSION_OVERLAP_MESSAGE =
            "Esa comisión ya tiene otro pedido en la misma fecha y horario.";

    private static final Duration EXAM_MIN_ADVANCE_NOTICE = Duration.ofHours(2);

    private ItemConsistency() {
    }

    public static <T> void requireDistinct(List<T> values, String what) {
        if (values.stream().filter(Objects::nonNull).distinct().count()
                != values.stream().filter(Objects::nonNull).count()) {
            throw new InvalidRoomRequestException("No se puede repetir " + what + " entre los pedidos de la solicitud.");
        }
    }

    public static boolean commissionScheduleOverlap(Long commissionA, LocalDate dateA, LocalTime startA, LocalTime endA,
                                                      Long commissionB, LocalDate dateB, LocalTime startB, LocalTime endB) {
        boolean sameCommission = !(commissionA == null && commissionB == null)
                && (commissionA == null || commissionB == null || commissionA.equals(commissionB));
        boolean sameDate = Objects.equals(dateA, dateB);
        boolean timeOverlap = startA.isBefore(endB) && startB.isBefore(endA);
        return sameCommission && sameDate && timeOverlap;
    }

    public static void requireNoCommissionOverlap(List<FreeFormItemDto> items) {
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                FreeFormItemDto a = items.get(i);
                FreeFormItemDto b = items.get(j);
                if (commissionScheduleOverlap(a.commissionId(), a.date(), a.startTime(), a.endTime(),
                        b.commissionId(), b.date(), b.startTime(), b.endTime())) {
                    throw new InvalidRoomRequestException(COMMISSION_OVERLAP_MESSAGE);
                }
            }
        }
    }

    public static void requireExactlyOne(int itemCount) {
        if (itemCount != 1) {
            throw new InvalidRoomRequestException("Este tipo de solicitud admite un solo pedido.");
        }
    }

    public static void requireNoCommission(CreateRoomRequestItemDto item, String message) {
        if (item.commissionId() != null) {
            throw new InvalidRoomRequestException(message);
        }
    }

    public static void requireExamUsersConsistent(boolean examType, CreateRoomRequestItemDto item) {
        boolean applies = examType && Boolean.TRUE.equals(item.requiresComputers());
        if (applies && item.requiresExamUsers() == null) {
            throw new InvalidRoomRequestException(
                    "requiresExamUsers es obligatorio en un pedido de examen que requiere computadoras.");
        }
        if (!applies && item.requiresExamUsers() != null) {
            throw new InvalidRoomRequestException(
                    "requiresExamUsers solo puede indicarse en un pedido de examen que además requiera computadoras.");
        }
    }

    public static void requireObservations(CreateRoomRequestItemDto item) {
        if (item.observations() == null || item.observations().isBlank()) {
            throw new InvalidRoomRequestException(
                    "observations es obligatorio en cada pedido para solicitudes de tipo OTHER.");
        }
    }

    public static void requireNotPast(LocalDate date, LocalTime startTime) {
        if (date.equals(LocalDate.now()) && startTime.isBefore(LocalTime.now())) {
            throw new InvalidRoomRequestException("No se puede solicitar un horario que ya pasó.");
        }
    }

    public static void requireExamAdvanceNotice(LocalDate date, LocalTime startTime) {
        if (LocalDateTime.of(date, startTime).isBefore(LocalDateTime.now().plus(EXAM_MIN_ADVANCE_NOTICE))) {
            throw new InvalidRoomRequestException(
                    "Los exámenes deben solicitarse con al menos 2 horas de anticipación.");
        }
    }

    /** typeLabel entra en jerga de negocio, p. ej. "cambio de aula por única vez". */
    public static void requireDateOnly(CreateRoomRequestItemDto item, String typeLabel) {
        if (item.date() == null) {
            throw new InvalidRoomRequestException("Cada pedido de " + typeLabel + " requiere una fecha.");
        }
        if (item.dayOfWeek() != null) {
            throw new InvalidRoomRequestException(
                    "El " + typeLabel + " se ata a una fecha, no a un día de dictado.");
        }
    }

    public static void requireDayOfWeekOnly(CreateRoomRequestItemDto item, String typeLabel) {
        if (item.dayOfWeek() == null) {
            throw new InvalidRoomRequestException("Cada pedido de " + typeLabel + " requiere un día de dictado.");
        }
        if (item.date() != null) {
            throw new InvalidRoomRequestException("El " + typeLabel + " no se ata a una fecha.");
        }
    }

    public static void requireNoEstimated(CreateRoomRequestItemDto item) {
        if (item.estimated() != null) {
            throw new InvalidRoomRequestException("El cambio de aula no lleva cantidad estimada de asistentes.");
        }
    }
}
