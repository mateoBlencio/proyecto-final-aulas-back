package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Consumo atómico de rotación: la condición {@code revoked = false} garantiza que sólo
     * un request concurrente afecte la fila. {@code replacedBy} se pasa como referencia
     * (no gestionada) porque el SET clause de JPQL sólo acepta asociaciones single-valued,
     * no un path anidado tipo {@code r.replacedBy.id}.
     */
    // clearAutomatically = true en las tres queries: un @Modifying bulk update no toca el
    // persistence context, así que sin esto un find posterior en la misma transacción
    // devolvería la entidad vieja cacheada (revoked=false) en vez de reconsultar la DB.
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :revokedAt, "
            + "r.revocationReason = ar.edu.utn.frc.siga.auth.model.RevocationReason.ROTATION, "
            + "r.replacedBy = :replacedBy "
            + "where r.tokenHash = :tokenHash and r.revoked = false")
    int consumeByTokenHash(@Param("tokenHash") String tokenHash,
                            @Param("replacedBy") RefreshToken replacedBy,
                            @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :revokedAt, "
            + "r.revocationReason = ar.edu.utn.frc.siga.auth.model.RevocationReason.CASCADE "
            + "where r.user.id = :userId and r.revoked = false")
    int revokeAllByUserId(@Param("userId") Integer userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :revokedAt, "
            + "r.revocationReason = ar.edu.utn.frc.siga.auth.model.RevocationReason.LOGOUT "
            + "where r.tokenHash = :tokenHash and r.revoked = false")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);
}
