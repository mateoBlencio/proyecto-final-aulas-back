package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;

import java.util.List;
import java.util.Objects;

/**
 * Consistencia entre los ítems de una solicitud (el front expande a N ítems y acá se chequea) y las
 * reglas por ítem que no dependen de la referencia académica ni del cursado.
 */
public final class ItemConsistency {

    private ItemConsistency() {
    }

    public static <T> void requireDistinct(List<T> values, String what) {
        if (values.stream().filter(Objects::nonNull).distinct().count()
                != values.stream().filter(Objects::nonNull).count()) {
            throw new InvalidRoomRequestException("No se puede repetir " + what + " entre los pedidos de la solicitud.");
        }
    }

    public static void requireExactlyOne(int itemCount) {
        if (itemCount != 1) {
            throw new InvalidRoomRequestException("Este tipo de solicitud admite un solo pedido.");
        }
    }

    /**
     * Si-y-sólo-si: en un examen con computadoras {@code requiresExamUsers} es obligatorio
     * ({@code null} = formulario incompleto); en cualquier otro caso tiene que venir {@code null}.
     */
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
}
