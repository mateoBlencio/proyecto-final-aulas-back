package ar.edu.utn.frc.siga.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import ar.edu.utn.frc.siga.auth.model.PasswordResetToken;
import ar.edu.utn.frc.siga.auth.repository.PasswordResetTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
class AuthUserLockOrderIntegrationTest {

    private static final String PASSWORD = "TestPassword123!";
    private static final String PASSWORD_HASH = "$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi";
    private static final String OTHER_HASH = "$2y$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";
    private static final String NEW_PASSWORD = "Cuaderno-Violeta-7391";
    private static final String WAITING_ON_USER_ROW = "select count(*) from pg_stat_activity "
            + "where pid <> pg_backend_pid() and datname = current_database() "
            + "and wait_event_type = 'Lock' and query ilike '%usuario%'";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private RestTemplate restTemplate;
    private String email;
    private Long userId;
    private String rawResetToken;

    @BeforeEach
    void createUser() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        email = "lockorder." + System.nanoTime() + "@frc.utn.edu.ar";
        userId = jdbc.queryForObject("insert into usuario (correo, password_hash, habilitado, nombre, apellido) "
                + "values (?, ?, true, 'Lock', 'Order') returning id_usuario", Long.class, email, PASSWORD_HASH);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("update refresh_token set reemplazado_por = null where id_usuario = ?", userId);
        jdbc.update("delete from refresh_token where id_usuario = ?", userId);
        jdbc.update("delete from token_recuperacion_clave where id_usuario = ?", userId);
    }

    @Test
    void login_userPasswordChangedWhileWaitingForTheUserLock_returns401AndIssuesNoRefreshToken() throws Exception {
        ResponseEntity<String> response = loginBlockedByUserLock(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(activeRefreshTokens()).isZero();
    }

    @Test
    void login_userUnchangedWhileWaitingForTheUserLock_returns200AndIssuesOneRefreshToken() throws Exception {
        ResponseEntity<String> response = loginBlockedByUserLock(false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeRefreshTokens()).isEqualTo(1);
    }

    @Test
    void reset_whileAnotherTransactionHoldsTheUserLock_waitsWithoutHoldingTheTokenLock() throws Exception {
        insertResetToken();
        CountDownLatch holdingLock = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> holder = pool.submit(userLockHolder(holdingLock, release, null));
            awaitLatch(holdingLock);
            Future<Void> reset = pool.submit(resetCall());

            awaitBlockedOnUser(reset);
            assertThat(reset.isDone()).isFalse();
            assertThatCode(this::lockResetTokenWithoutWaiting).doesNotThrowAnyException();

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            reset.get(30, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from token_recuperacion_clave where id_usuario = ? and fecha_uso is not null",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void reset_concurrentWithTransactionLockingUserThenToken_completesWithoutDeadlock() throws Exception {
        insertResetToken();
        CountDownLatch holdingLock = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> userThenToken = pool.submit(userLockHolder(holdingLock, release, this::lockResetTokenInTransaction));
            awaitLatch(holdingLock);
            Future<Void> reset = pool.submit(resetCall());

            awaitBlockedOnUser(reset);
            release.countDown();

            assertThatCode(() -> userThenToken.get(30, TimeUnit.SECONDS)).doesNotThrowAnyException();
            assertThatCode(() -> reset.get(30, TimeUnit.SECONDS)).doesNotThrowAnyException();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from token_recuperacion_clave where id_usuario = ? and fecha_uso is not null",
                Integer.class, userId)).isEqualTo(1);
    }

    private ResponseEntity<String> loginBlockedByUserLock(boolean changePasswordHash) throws Exception {
        CountDownLatch holdingLock = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable changeHash = changePasswordHash ? this::changePasswordHashInTransaction : null;

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Void> holder = pool.submit(userLockHolder(holdingLock, release, changeHash));
            awaitLatch(holdingLock);
            Future<ResponseEntity<String>> login = pool.submit(this::login);

            awaitBlockedOnUser(login);
            assertThat(login.isDone()).isFalse();

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            return login.get(30, TimeUnit.SECONDS);
        }
    }

    private Callable<Void> userLockHolder(CountDownLatch holdingLock, CountDownLatch release, Runnable beforeCommit) {
        return () -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                entityManager.createNativeQuery("select id_usuario from usuario where id_usuario = :id for no key update")
                        .setParameter("id", userId)
                        .getResultList();
                holdingLock.countDown();
                awaitLatch(release);
                if (beforeCommit != null) {
                    beforeCommit.run();
                }
            });
            return null;
        };
    }

    private void changePasswordHashInTransaction() {
        entityManager.createNativeQuery("update usuario set password_hash = :hash where id_usuario = :id")
                .setParameter("hash", OTHER_HASH)
                .setParameter("id", userId)
                .executeUpdate();
    }

    private void lockResetTokenInTransaction() {
        entityManager.createNativeQuery("select id_token_recuperacion from token_recuperacion_clave "
                        + "where token_hash = :hash for update")
                .setParameter("hash", OpaqueTokens.hash(rawResetToken))
                .getResultList();
    }

    private void lockResetTokenWithoutWaiting() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("select id_token_recuperacion from token_recuperacion_clave "
                                + "where token_hash = :hash for update nowait")
                        .setParameter("hash", OpaqueTokens.hash(rawResetToken))
                        .getResultList());
    }

    private Callable<Void> resetCall() {
        String ip = "lockorder-" + userId;
        return () -> {
            passwordResetService.reset(rawResetToken, NEW_PASSWORD, ip);
            return null;
        };
    }

    private void insertResetToken() {
        rawResetToken = OpaqueTokens.generate();
        tokenRepository.save(PasswordResetToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(OpaqueTokens.hash(rawResetToken))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build());
    }

    private ResponseEntity<String> login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("http://localhost:" + port + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD), headers), String.class);
    }

    private void awaitBlockedOnUser(Future<?> waiter) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> {
            if (waiter.isDone()) {
                waiter.get();
            }
            assertThat(jdbc.queryForObject(WAITING_ON_USER_ROW, Integer.class)).isPositive();
        });
    }

    private int activeRefreshTokens() {
        return jdbc.queryForObject("select count(*) from refresh_token where id_usuario = ? and not revocado",
                Integer.class, userId);
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
