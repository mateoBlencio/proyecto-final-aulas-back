package ar.edu.utn.frc.siga.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.dto.request.RefreshTokenRequest;
import ar.edu.utn.frc.siga.auth.exception.InvalidRefreshTokenException;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.RevocationReason;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Not @Transactional on purpose: the cascade revocation must be committed in the DB
 * even though the service throws afterwards.
 */
class RefreshTokenReuseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.save(User.builder()
                .email("reuse.test@frc.utn.edu.ar")
                .passwordHash("irrelevant")
                .enabled(true)
                .firstName("Reuse")
                .lastName("Test")
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("update refresh_token set reemplazado_por = null where id_usuario = ?", user.getId());
        jdbc.update("delete from refresh_token where id_usuario = ?", user.getId());
        jdbc.update("delete from usuario where id_usuario = ?", user.getId());
    }

    @Test
    void refresh_reusedTokenOutsideGraceWindow_revokesTheWholeFamilyInTheDb() {
        String first = refreshTokenService.issue(user).rawToken();
        String second = authService.refresh(new RefreshTokenRequest(first)).getRefreshToken();
        jdbc.update("update refresh_token set fecha_revocacion = now() - interval '1 hour' where id_usuario = ? and revocado",
                user.getId());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(first)))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .allMatch(t -> Boolean.TRUE.equals(t.getRevoked()))).isTrue();
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(second)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_disabledUser_revokesAllTokensInTheDb() {
        String raw = refreshTokenService.issue(user).rawToken();
        jdbc.update("update usuario set habilitado = false where id_usuario = ?", user.getId());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(raw)))
                .isInstanceOf(InvalidRefreshTokenException.class);

        Integer active = jdbc.queryForObject(
                "select count(*) from refresh_token where id_usuario = ? and not revocado", Integer.class, user.getId());
        assertThat(active).isZero();
    }

    @Test
    void revokeAllByUserId_marksRotatedTokensAsCascadeKeepingTheirOriginalRevokedAt() {
        String first = refreshTokenService.issue(user).rawToken();
        String second = authService.refresh(new RefreshTokenRequest(first)).getRefreshToken();
        Instant rotatedAt = refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(first)).orElseThrow().getRevokedAt();

        refreshTokenService.revokeAllByUserId(user.getId());

        RefreshToken rotated = refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(first)).orElseThrow();
        RefreshToken active = refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(second)).orElseThrow();
        assertThat(rotated.getRevocationReason()).isEqualTo(RevocationReason.CASCADE);
        assertThat(rotated.getRevokedAt()).isEqualTo(rotatedAt);
        assertThat(active.getRevocationReason()).isEqualTo(RevocationReason.CASCADE);
        assertThat(active.getRevokedAt()).isAfter(rotatedAt);
    }

    @Test
    void refresh_rotatedTokenWithinGraceWindow_isRejectedAfterRevokeAll() {
        String first = refreshTokenService.issue(user).rawToken();
        authService.refresh(new RefreshTokenRequest(first));

        refreshTokenService.revokeAllByUserId(user.getId());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(first)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_rotatedTokenWithinGraceWindow_isAcceptedWithoutRevokeAll() {
        String first = refreshTokenService.issue(user).rawToken();
        authService.refresh(new RefreshTokenRequest(first));

        assertThat(authService.refresh(new RefreshTokenRequest(first)).getRefreshToken()).isNotBlank();
    }

    @Test
    void revokeAllByUserId_waitsForUserRowLockHeldByAnotherTransaction() throws Exception {
        String raw = refreshTokenService.issue(user).rawToken();

        runBlockedByUserRowLock(() -> {
            refreshTokenService.revokeAllByUserId(user.getId());
            return null;
        });

        assertThat(refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(raw)).orElseThrow().getRevoked()).isTrue();
    }

    @Test
    void refresh_activeToken_waitsForUserRowLockHeldByAnotherTransaction() throws Exception {
        String raw = refreshTokenService.issue(user).rawToken();

        RefreshTokenService.RefreshResult result = runBlockedByUserRowLock(() -> refreshTokenService.refresh(raw));

        assertThat(result.refreshToken().rawToken()).isNotEqualTo(raw);
    }

    @Test
    void refresh_rotatedTokenWithinGraceWindow_waitsForUserRowLockHeldByAnotherTransaction() throws Exception {
        String first = refreshTokenService.issue(user).rawToken();
        refreshTokenService.refresh(first);

        RefreshTokenService.RefreshResult result = runBlockedByUserRowLock(() -> refreshTokenService.refresh(first));

        assertThat(result.refreshToken().rawToken()).isNotEqualTo(first);
    }

    private <T> T runBlockedByUserRowLock(Callable<T> action) throws Exception {
        CountDownLatch holdingLock = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Callable<Void> holder = () -> {
            txTemplate.executeWithoutResult(status -> {
                entityManager.createNativeQuery("select id_usuario from usuario where id_usuario = :id for no key update")
                        .setParameter("id", user.getId())
                        .getResultList();
                holdingLock.countDown();
                awaitLatch(release);
            });
            return null;
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> futureHolder = pool.submit(holder);
            awaitLatch(holdingLock);
            Future<T> futureAction = pool.submit(action);

            assertThatThrownBy(() -> futureAction.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            release.countDown();
            futureHolder.get(30, TimeUnit.SECONDS);
            return futureAction.get(30, TimeUnit.SECONDS);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout esperando el latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
