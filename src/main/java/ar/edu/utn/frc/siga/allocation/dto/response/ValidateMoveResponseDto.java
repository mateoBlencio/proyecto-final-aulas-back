package ar.edu.utn.frc.siga.allocation.dto.response;

import java.util.List;

/**
 * Resultado de validar el movimiento de un evento a otra aula sobre el preview
 * automático. Responde 200 siempre que el request sea coherente con el preview: el
 * conflicto es el resultado esperado de la interacción de arrastre, no un error, por eso
 * viaja en el body ({@code valid=false} + {@code conflicts}) y nunca como 409.
 */
public record ValidateMoveResponseDto(boolean valid, List<MoveConflictDto> conflicts) {
}
