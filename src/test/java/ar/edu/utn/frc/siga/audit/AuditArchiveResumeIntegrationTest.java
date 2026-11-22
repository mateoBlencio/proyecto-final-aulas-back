package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.AuditArchiveFixture.Scenario;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.repository.ArchivedTable;
import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRepository;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Interrupted runs, with batches of 2 rows so R1 (3 occurrences) is split across batches. Needs its own
 * context to lower {@code batch-size}.
 */
@TestPropertySource(properties = "siga.audit.archive.batch-size=2")
@DisplayName("Archivado de auditoría: corte a mitad y reanudación (integración)")
class AuditArchiveResumeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditArchiveService archiveService;
    @Autowired
    private AuditArchiveRepository archiveRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    private AuditArchiveFixture fixture;
    private Scenario s;

    @BeforeEach
    void seed() {
        fixture = new AuditArchiveFixture(jdbc);
        fixture.begin();
        fixture.seedPeriods();
        s = fixture.seedScenario();
    }

    @AfterEach
    void cleanUp() {
        fixture.end();
    }

    private AuditArchiveResultDto archiveAsSystem() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        return archiveService.archive(AuditArchiveFixture.TODAY);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void commitOneOccurrenceBatch() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE,
                        AuditArchiveFixture.CUTOFF.atStartOfDay(), 2));
    }

    private void assertArchivedExactlyOnce() {
        assertThat(count("SELECT count(*) FROM archivo.ocurrencia_aud")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM (SELECT DISTINCT rev, id_ocurrencia FROM archivo.ocurrencia_aud) d"))
                .isEqualTo(5);
        assertThat(count("SELECT count(*) FROM archivo.asignacion_aula_aud")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM public.ocurrencia_aud WHERE rev IN (?, ?)", s.r1(), s.r2())).isZero();
        assertThat(count("SELECT count(*) FROM public.asignacion_aula_aud WHERE rev IN (?, ?)", s.r1(), s.r3())).isZero();
    }

    @Test
    @DisplayName("tras un solo lote R1 conserva su revinfo y su resumen de ocurrencias baja de 3 a 1")
    void afterOneBatchTheRevisionIsStillOnline() {
        commitOneOccurrenceBatch();

        assertThat(count("SELECT count(*) FROM public.revinfo WHERE rev = ?", s.r1())).isEqualTo(1);
        assertThat(count("SELECT cantidad FROM public.revinfo_resumen WHERE rev = ? AND tabla_aud = 'ocurrencia_aud'",
                s.r1())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM public.ocurrencia_aud WHERE rev = ?", s.r1())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM archivo.ocurrencia_aud WHERE rev = ?", s.r1())).isEqualTo(2);
        assertThat(count("SELECT cantidad FROM archivo.revinfo_resumen WHERE rev = ? AND tabla_aud = 'ocurrencia_aud'",
                s.r1())).isEqualTo(3);
    }

    @Test
    @DisplayName("un segundo lote que se revierte no cambia nada y la corrida siguiente completa sin duplicar")
    void rolledBackSecondBatchThenCompleteRun() {
        commitOneOccurrenceBatch();
        Map<String, Long> afterFirstBatch = fixture.counts();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            archiveRepository.archiveBatch(ArchivedTable.OCCURRENCE, AuditArchiveFixture.CUTOFF.atStartOfDay(), 2);
            throw new IllegalStateException("interrupted");
        })).hasMessage("interrupted");
        assertThat(fixture.counts()).isEqualTo(afterFirstBatch);

        AuditArchiveResultDto result = archiveAsSystem();

        assertThat(result).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.COMPLETED,
                AuditArchiveFixture.RETAINED_FROM_CYCLE, AuditArchiveFixture.CUTOFF, 3, 3, 2));
        assertArchivedExactlyOnce();
        assertThat(count("SELECT count(*) FROM public.revinfo WHERE rev IN (?, ?)", s.r1(), s.r3())).isZero();
        assertThat(count("SELECT count(*) FROM archivo.revinfo")).isEqualTo(3);
    }

    @Test
    @DisplayName("una fila de R2 copiada a mano a archivo antes de la corrida no se duplica")
    void rowCopiedByHandBeforeTheRunIsNotDuplicated() {
        commitOneOccurrenceBatch();
        jdbc.update("INSERT INTO archivo.revinfo SELECT r.* FROM public.revinfo r WHERE r.rev = ? ON CONFLICT DO NOTHING",
                s.r2());
        jdbc.update("INSERT INTO archivo.ocurrencia_aud SELECT a.* FROM public.ocurrencia_aud a "
                + "WHERE a.rev = ? AND a.id_ocurrencia = 9011", s.r2());

        AuditArchiveResultDto result = archiveAsSystem();

        assertThat(result.outcome()).isEqualTo(AuditArchiveOutcome.COMPLETED);
        assertThat(result.occurrenceRows()).isEqualTo(3);
        assertArchivedExactlyOnce();
    }

    @Test
    @DisplayName("si el segundo lote falla, el registro queda FAILED con las filas del primero y lo archivado en el primero se conserva")
    void failureInTheSecondBatchLeavesAFailedRun() {
        // Batch 1 holds R1 ids 9001 and 9002; batch 2 starts with 9003 and 9011, which already exists with other content.
        jdbc.update("INSERT INTO archivo.ocurrencia_aud (id_ocurrencia, rev, revtype, id_evento_academico, fecha, estado) "
                + "VALUES (9011, ?, 0, 999, DATE '2002-06-10', 'ROOM_RELEASED')", s.r2());

        assertThatThrownBy(this::archiveAsSystem)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");

        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM archivado_auditoria");
        assertThat(run.get("estado")).isEqualTo("FAILED");
        assertThat(((Number) run.get("filas_ocurrencia")).longValue()).isEqualTo(2);
        assertThat((String) run.get("mensaje_error")).contains("no coincide");
        assertThat(count("SELECT count(*) FROM archivo.ocurrencia_aud WHERE rev = ? AND id_ocurrencia IN (9001, 9002)",
                s.r1())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM public.ocurrencia_aud WHERE rev = ? AND id_ocurrencia IN (9001, 9002)",
                s.r1())).isZero();
        assertThat(count("SELECT count(*) FROM public.ocurrencia_aud WHERE rev = ? AND id_ocurrencia = 9003", s.r1()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM public.ocurrencia_aud WHERE rev = ?", s.r2())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM revinfo r JOIN archivado_auditoria_aud a ON a.rev = r.rev "
                + "ORDER BY r.rev LIMIT 1", String.class)).contains("falló tras 2 filas");

        jdbc.update("DELETE FROM archivo.ocurrencia_aud WHERE id_ocurrencia = 9011");
        AuditArchiveResultDto retry = archiveAsSystem();

        assertThat(retry.outcome()).isEqualTo(AuditArchiveOutcome.COMPLETED);
        assertThat(retry.occurrenceRows()).isEqualTo(3);
        assertArchivedExactlyOnce();
    }
}
