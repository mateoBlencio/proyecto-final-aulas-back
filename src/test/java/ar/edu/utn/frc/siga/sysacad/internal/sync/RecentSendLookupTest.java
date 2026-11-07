package ar.edu.utn.frc.siga.sysacad.internal.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecentSendLookup")
class RecentSendLookupTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private SysacadScheduleLogRepository logRepository;

    private RecentSendLookup lookup;

    @BeforeEach
    void setUp() {
        SysacadProperties properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.ON));
        lookup = new RecentSendLookup(logRepository, properties);
    }

    private static SysacadSchedule schedule(Long id) {
        return SysacadSchedule.builder().id(id).year(2026).specialty(5).plan(2008).subject(115).commission(7)
                .dayOfWeek(1).semester(1).knownBuilding(5).knownRoom(228).build();
    }

    @Test
    @DisplayName("El envío en curso del horario con ese edificio y aula es reciente sin consultar el registro")
    void sendingValuesMatchWithoutQueryingLog() {
        SysacadSchedule schedule = schedule(1L);
        schedule.setSendingBuilding(5);
        schedule.setSendingRoom(229);

        assertThat(lookup.isRecentSend(schedule, 5, 229, NOW)).isTrue();

        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("Un envío en curso a otra aula no cuenta y se consulta el registro")
    void sendingToOtherRoomFallsBackToLog() {
        SysacadSchedule schedule = schedule(1L);
        schedule.setSendingBuilding(5);
        schedule.setSendingRoom(230);

        assertThat(lookup.isRecentSend(schedule, 5, 229, NOW)).isFalse();

        verify(logRepository).existsRecentIntent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("El mismo número de aula con otro edificio en el envío en curso no cuenta")
    void sendingInOtherBuildingDoesNotMatch() {
        SysacadSchedule schedule = schedule(1L);
        schedule.setSendingBuilding(6);
        schedule.setSendingRoom(229);

        assertThat(lookup.isRecentSend(schedule, 5, 229, NOW)).isFalse();
    }

    @Test
    @DisplayName("Un SEND_INTENT dentro de la ventana de recentSendWindow en el registro es un envío reciente")
    void loggedIntentInsideWindowIsRecent() {
        when(logRepository.existsRecentIntent(1L, 5, 229, NOW.minus(Duration.ofDays(7)))).thenReturn(true);

        assertThat(lookup.isRecentSend(schedule(1L), 5, 229, NOW)).isTrue();
    }

    @Test
    @DisplayName("Sin SEND_INTENT dentro de la ventana no es un envío reciente")
    void noLoggedIntentInsideWindowIsNotRecent() {
        when(logRepository.existsRecentIntent(1L, 5, 229, NOW.minus(Duration.ofDays(7)))).thenReturn(false);

        assertThat(lookup.isRecentSend(schedule(1L), 5, 229, NOW)).isFalse();
    }

    @Test
    @DisplayName("Un horario sin id no consulta el registro")
    void scheduleWithoutIdSkipsLog() {
        assertThat(lookup.isRecentSend(schedule(null), 5, 229, NOW)).isFalse();

        verify(logRepository, never()).existsRecentIntent(any(), any(), any(), any());
    }
}
