package ar.edu.utn.frc.siga.allocation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Qué pasaría si se aplicara el pedido, sin aplicarlo.
 *
 * <p>Responde las dos cosas que un 409 no puede: <b>cuántas clases toca</b> el pedido — el rango
 * se expande en el servidor, así que el front no tiene forma de saber el denominador — y el
 * detalle de cada conflicto <i>como dato</i> y no como error, con las aulas alternativas para
 * destrabarlo.
 *
 * <p>La escritura sigue siendo todo o nada: mientras {@code blockedClasses} sea mayor que cero, el
 * PUT equivalente va a fallar entero. {@code movableClasses} dice cuántas se salvarían si se
 * resolvieran los conflictos, no que se vayan a aplicar sueltas.
 *
 * @param totalClasses   ocurrencias que el pedido alcanza
 * @param movableClasses cuántas de ellas no tienen conflicto
 * @param blockedClasses cuántas están bloqueadas; {@code 0} significa que el pedido se aplicaría
 * @param occurrences    el plan completo, en orden de fecha
 * @param conflicts      solo las bloqueadas, con quién las bloquea y a qué aula podrían ir
 */
@Schema(description = "Vista previa del impacto de un pedido de asignación, sin escribir nada")
public record AllocationImpactResponseDto(
        int totalClasses,
        int movableClasses,
        int blockedClasses,
        List<ImpactOccurrenceDto> occurrences,
        List<ImpactConflictDto> conflicts) {
}
