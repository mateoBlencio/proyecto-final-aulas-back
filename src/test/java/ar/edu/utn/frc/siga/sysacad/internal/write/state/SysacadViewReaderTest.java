package ar.edu.utn.frc.siga.sysacad.internal.write.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.ReconcileOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.DryRunChange;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadViewReconciler;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadViewReader.ViewReadResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadViewReader")
class SysacadViewReaderTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ScheduleViewFetcher fetcher;
    @Mock
    private SysacadScheduleRepository scheduleRepository;
    @Mock
    private SysacadViewReconciler reconciler;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SysacadProperties properties;
    private SysacadViewReader reader;

    @BeforeEach
    void setUp() {
        properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.ON));
        reader = new SysacadViewReader(fetcher, new SysacadCatalogMapper(), scheduleRepository, reconciler,
                properties, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SysacadSchedule schedule(long id, int subject) {
        return SysacadSchedule.builder().id(id).year(2026).specialty(5).plan(2008).subject(subject).commission(7)
                .dayOfWeek(1).semester(1).knownBuilding(5).knownRoom(228).build();
    }

    private static RawSchedule raw(int subject, int building, int room) {
        return new RawSchedule("1K1", 7, room, building, "Edificio", 1, 1, "A", "A", "08:00", "10:15",
                "08:00-10:15", 135, 5, "Sistemas", 2008, subject, "Materia", 0);
    }

    @Test
    @DisplayName("Reconcilia cada horario presente con el edificio y el aula de sus filas, en una transacción")
    void reconcilesWithRoomOfItsRows() {
        SysacadSchedule schedule = schedule(1, 115);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));
        when(reconciler.reconcile(schedule, 5, 229, NOW)).thenReturn(ReconcileOutcome.NO_CHANGE);

        ViewReadResult result = reader.read();

        verify(reconciler).reconcile(schedule, 5, 229, NOW);
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(any());
        assertThat(result.directChangeScheduleIds()).isEmpty();
        assertThat(result.dryRunChanges()).isEmpty();
    }

    @Test
    @DisplayName("Varias filas de la clave con la misma aula cuentan como una sola")
    void duplicateRowsWithSameRoomReconcileOnce() {
        SysacadSchedule schedule = schedule(1, 115);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229), raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));

        reader.read();

        verify(reconciler).reconcile(schedule, 5, 229, NOW);
    }

    @Test
    @DisplayName("Devuelve el horario como cambio directo cuando el reconciliador lo detecta")
    void returnsDirectChange() {
        SysacadSchedule schedule = schedule(1, 115);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));
        when(reconciler.reconcile(schedule, 5, 229, NOW)).thenReturn(ReconcileOutcome.DIRECT_CHANGE);

        ViewReadResult result = reader.read();

        assertThat(result.directChangeScheduleIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("En DRY_RUN un cambio externo aplicado se devuelve con el aula previa, tomada antes de reconciliar")
    void dryRunReturnsExternalChangeWithPreviousRoom() {
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.DRY_RUN));
        SysacadSchedule schedule = schedule(1, 115);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));
        when(reconciler.reconcile(schedule, 5, 229, NOW)).thenAnswer(invocation -> {
            schedule.setKnownRoom(229);
            return ReconcileOutcome.EXTERNAL_CHANGE_APPLIED;
        });

        ViewReadResult result = reader.read();

        assertThat(result.dryRunChanges()).containsExactly(new DryRunChange(1L, 5, 228, 5, 229));
        assertThat(result.directChangeScheduleIds()).isEmpty();
    }

    @Test
    @DisplayName("En ON un cambio externo aplicado no se devuelve como simulado")
    void onDoesNotReturnDryRunChanges() {
        SysacadSchedule schedule = schedule(1, 115);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));
        when(reconciler.reconcile(schedule, 5, 229, NOW)).thenReturn(ReconcileOutcome.EXTERNAL_CHANGE_APPLIED);

        assertThat(reader.read().dryRunChanges()).isEmpty();
    }

    @Test
    @DisplayName("Saltea las claves con filas de distinto edificio o aula y reconcilia las demás")
    void skipsKeysWithDivergentRows() {
        SysacadSchedule divergent = schedule(1, 115);
        SysacadSchedule healthy = schedule(2, 116);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229), raw(115, 5, 230), raw(116, 5, 231)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(divergent, healthy));

        reader.read();

        verify(reconciler, never()).reconcile(eq(divergent), any(), any(), any());
        verify(reconciler).reconcile(healthy, 5, 231, NOW);
    }

    @Test
    @DisplayName("Saltea las claves con filas en distinto edificio aunque el número de aula coincida")
    void skipsKeysWithRowsInDifferentBuildings() {
        SysacadSchedule schedule = schedule(1, 115);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229), raw(115, 6, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));

        reader.read();

        verifyNoInteractions(reconciler);
    }

    @Test
    @DisplayName("No toca los horarios con KEY_CONFLICT")
    void doesNotTouchKeyConflict() {
        SysacadSchedule conflicting = schedule(1, 115);
        conflicting.setNotSendableReason(NotSendableReason.KEY_CONFLICT);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(conflicting));

        reader.read();

        verifyNoInteractions(reconciler);
    }

    @Test
    @DisplayName("No toca los horarios que ya no tienen filas en la vista")
    void doesNotTouchSchedulesAbsentFromView() {
        SysacadSchedule absent = schedule(1, 999);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(absent));

        reader.read();

        verifyNoInteractions(reconciler);
    }

    @Test
    @DisplayName("Un horario con otro motivo de no enviable sí se reconcilia")
    void otherNotSendableReasonStillReconciles() {
        SysacadSchedule schedule = schedule(1, 115);
        schedule.setNotSendableReason(NotSendableReason.ROOM_UNKNOWN);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229)));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));

        reader.read();

        verify(reconciler).reconcile(schedule, 5, 229, NOW);
    }

    @Test
    @DisplayName("Las filas ilegibles se descartan antes de agrupar")
    void unreadableRowsAreDiscarded() {
        SysacadSchedule schedule = schedule(1, 115);
        RawSchedule unreadable = new RawSchedule("1K1", 7, 230, 5, "Edificio", 1, 1, "A", "A", "no-es-hora", "10:15",
                "x", 135, 5, "Sistemas", 2008, 115, "Materia", 0);
        when(fetcher.fetch()).thenReturn(List.of(raw(115, 5, 229), unreadable));
        when(scheduleRepository.findByPresentInViewTrue()).thenReturn(List.of(schedule));

        reader.read();

        verify(reconciler).reconcile(schedule, 5, 229, NOW);
    }

    @Test
    @DisplayName("Una vista vacía propaga la falla y no toca ningún horario")
    void emptyViewPropagatesFailure() {
        when(fetcher.fetch()).thenReturn(List.of());

        assertThatThrownBy(() -> reader.read()).isInstanceOf(SysacadEmptyViewException.class);

        verifyNoInteractions(scheduleRepository, reconciler, transactionManager);
    }

    @Test
    @DisplayName("Un error de red al leer la vista se propaga y no toca ningún horario")
    void fetchFailurePropagates() {
        when(fetcher.fetch()).thenThrow(new IllegalStateException("sin red"));

        assertThatThrownBy(() -> reader.read()).hasMessage("sin red");

        verifyNoInteractions(scheduleRepository, reconciler);
        verify(reconciler, never()).reconcile(any(), anyInt(), anyInt(), any());
    }
}
