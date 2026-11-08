package ar.edu.utn.frc.siga.sysacad.internal.write.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadViewReconciler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.AffectedOccurrence;

@DisplayName("SysacadScheduleStateWriter: resolución de revisión")
class SysacadScheduleStateWriterResolveTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    private final SysacadScheduleRepository scheduleRepository = mock(SysacadScheduleRepository.class);
    private final SysacadScheduleLogRepository logRepository = mock(SysacadScheduleLogRepository.class);
    private SysacadScheduleStateWriter writer;
    private SysacadSchedule schedule;

    @BeforeEach
    void setUp() {
        writer = new SysacadScheduleStateWriter(scheduleRepository, logRepository, mock(SysacadViewReconciler.class),
                JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
        schedule = SysacadSchedule.builder().id(7L).year(2026).specialty(5).plan(2008).subject(115).commission(7)
                .dayOfWeek(1).semester(1).knownBuilding(5).knownRoom(228).baseBuilding(5).baseRoom(228)
                .differentBuilding(6).differentRoom(300).differenceDetectedAt(NOW.minusSeconds(900))
                .status(SysacadScheduleStatus.REVIEW).reviewReason(SysacadReviewReason.ROOM_OCCUPIED)
                .reviewSince(NOW.minusSeconds(3600)).attempts(4).nextAttemptAt(NOW).lastError("NOT_CONFIRMED")
                .sendingBuilding(5).sendingRoom(229).build();
        when(scheduleRepository.findById(7L)).thenReturn(Optional.of(schedule));
    }

    private SysacadScheduleLogEntry savedRow() {
        ArgumentCaptor<SysacadScheduleLogEntry> captor = ArgumentCaptor.forClass(SysacadScheduleLogEntry.class);
        verify(logRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("resendSiga: la última lectura pasa a valor conocido, la base queda intacta y se limpian intentos y envío")
    void resendSigaTakesLastReadAsKnown() {
        writer.resolveResendSiga(7L, "sub@frc.utn.edu.ar");

        assertThat(schedule.getKnownBuilding()).isEqualTo(6);
        assertThat(schedule.getKnownRoom()).isEqualTo(300);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
        assertThat(schedule.getAttempts()).isZero();
        assertThat(schedule.getNextAttemptAt()).isNull();
        assertThat(schedule.getLastError()).isNull();
        assertThat(schedule.getSendingRoom()).isNull();
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(schedule.getDifferenceDetectedAt()).isNull();
        assertThat(schedule.getReviewReason()).isNull();
        assertThat(schedule.getReviewSince()).isNull();
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
    }

    @Test
    @DisplayName("resendSiga: deja una fila REVIEW_RESOLVED con la acción, el usuario y el aula anterior y nueva")
    void resendSigaWritesReviewResolvedRow() {
        writer.resolveResendSiga(7L, "sub@frc.utn.edu.ar");

        SysacadScheduleLogEntry row = savedRow();
        assertThat(row.getType()).isEqualTo(ScheduleLogType.REVIEW_RESOLVED);
        assertThat(row.getReason()).isEqualTo("RESEND_SIGA");
        assertThat(row.getResolvedBy()).isEqualTo("sub@frc.utn.edu.ar");
        assertThat(row.getPreviousRoom()).isEqualTo(228);
        assertThat(row.getNewBuilding()).isEqualTo(6);
        assertThat(row.getNewRoom()).isEqualTo(300);
    }

    @Test
    @DisplayName("resendSiga sin diferencia registrada usa el valor conocido")
    void resendSigaWithoutDifferenceKeepsKnown() {
        schedule.setDifferentBuilding(null);
        schedule.setDifferentRoom(null);

        writer.resolveResendSiga(7L, "sub@frc.utn.edu.ar");

        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
    }

    @Test
    @DisplayName("keepSysacad: la última lectura pasa a conocido y a aula base")
    void keepSysacadUpdatesKnownAndBase() {
        writer.resolveKeepSysacad(7L, "sub@frc.utn.edu.ar", List.of());

        assertThat(schedule.getKnownRoom()).isEqualTo(300);
        assertThat(schedule.getBaseBuilding()).isEqualTo(6);
        assertThat(schedule.getBaseRoom()).isEqualTo(300);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(savedRow().getReason()).isEqualTo("KEEP_SYSACAD");
    }

    @Test
    @DisplayName("keepSysacad con pedidos pisados: además deja una fila SIGA_CHANGES_ANNULLED con las clases")
    void keepSysacadLogsAnnulledClasses() {
        writer.resolveKeepSysacad(7L, "sub@frc.utn.edu.ar",
                List.of(new AffectedOccurrence(1L, LocalDate.of(2026, 4, 6), 50L)));

        ArgumentCaptor<SysacadScheduleLogEntry> captor = ArgumentCaptor.forClass(SysacadScheduleLogEntry.class);
        verify(logRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysacadScheduleLogEntry::getType)
                .containsExactly(ScheduleLogType.SIGA_CHANGES_ANNULLED, ScheduleLogType.REVIEW_RESOLVED);
        assertThat(captor.getAllValues().getFirst().getOccurrences()).contains("\"occurrenceId\":1");
    }

    @Test
    @DisplayName("recordCommissionMessage: registra el texto, el resultado y la respuesta sin tocar el estado del horario")
    void recordCommissionMessageKeepsScheduleState() {
        writer.recordCommissionMessage(7L, 100L, 5, 229, "Cambio de aula", false, "HTTP 500: error");

        SysacadScheduleLogEntry row = savedRow();
        assertThat(row.getType()).isEqualTo(ScheduleLogType.COMMISSION_MESSAGE);
        assertThat(row.getResult()).isEqualTo("FAILED");
        assertThat(row.getReason()).isEqualTo("Cambio de aula");
        assertThat(row.getResponse()).isEqualTo("HTTP 500: error");
        assertThat(row.getOccurrenceId()).isEqualTo(100L);
        assertThat(row.getNewRoom()).isEqualTo(229);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
        assertThat(schedule.getAttempts()).isEqualTo(4);
    }
}
