package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :revokedAt, "
            + "r.revocationReason = ar.edu.utn.frc.siga.auth.model.RevocationReason.ROTATION, "
            + "r.replacedBy = :replacedBy "
            + "where r.tokenHash = :tokenHash and r.revoked = false")
    int consumeByTokenHash(@Param("tokenHash") String tokenHash,
                            @Param("replacedBy") RefreshToken replacedBy,
                            @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :revokedAt, "
            + "r.revocationReason = ar.edu.utn.frc.siga.auth.model.RevocationReason.CASCADE "
            + "where r.user.id = :userId and r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :revokedAt, "
            + "r.revocationReason = ar.edu.utn.frc.siga.auth.model.RevocationReason.LOGOUT "
            + "where r.tokenHash = :tokenHash and r.revoked = false")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);
}
