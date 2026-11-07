package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.Collection;
import org.springframework.modulith.NamedInterface;

/**
 * {@code classroomId} nulo deja las clases futuras sin aula (quedan pendientes de aula, no liberadas).
 * {@code applyManualRules} aplica la regla manual de solapamiento, que puede lanzar su excepción; sin ella, un
 * aula ocupada se devuelve como {@link SysacadDirectChangeResult.Occupied}.
 */
@NamedInterface("api")
public record SysacadDirectChangeCommand(Collection<Long> eventIds, Long classroomId, String reason, String actor,
        boolean applyManualRules) {
}
