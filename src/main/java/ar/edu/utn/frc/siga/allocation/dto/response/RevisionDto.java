package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDateTime;

/**
 * Una revisión de auditoría de Envers: quién la provocó, cuándo, qué operación fue y el
 * estado de la entidad en ese momento.
 *
 * @param revision número de revisión global ({@code revinfo.rev})
 * @param date     momento de la revisión
 * @param user     email del usuario autenticado; null si la transacción no vino de un request autenticado
 * @param kind     operación registrada (CREATED/MODIFIED/DELETED)
 * @param snapshot estado de la entidad en esa revisión; null en DELETED (Envers no guarda el estado final)
 */
public record RevisionDto<T>(
        Integer revision,
        LocalDateTime date,
        String user,
        RevisionKind kind,
        T snapshot) {
}
