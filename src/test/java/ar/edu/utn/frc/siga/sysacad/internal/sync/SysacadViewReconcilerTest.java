package ar.edu.utn.frc.siga.sysacad.internal.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

@DisplayName("SysacadViewReconciler")
class SysacadViewReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-03-01T08:00:00Z");

    private static SysacadViewReconciler reconcilerIn(SysacadWriteMode mode) {
        return reconcilerIn(mode, mock(SysacadScheduleLogRepository.class));
    }

    private static SysacadViewReconciler reconcilerIn(SysacadWriteMode mode, SysacadScheduleLogRepository logRepository) {
        SysacadProperties properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(mode));
        return new SysacadViewReconciler(properties, new RecentSendLookup(logRepository, properties), logRepository);
    }

    private static SysacadSchedule schedule() {
        return SysacadSchedule.builder()
                .year(2026).specialty(5).plan(2008).subject(115).commission(7).dayOfWeek(1).semester(1)
                .knownBuilding(5).knownRoom(228).baseBuilding(5).baseRoom(228)
                .build();
    }

    private static SysacadSchedule schedule(SysacadScheduleStatus status) {
        SysacadSchedule schedule = schedule();
        schedule.setStatus(status);
        return schedule;
    }

    private static SysacadSchedule sendingTo229(SysacadScheduleStatus status) {
        SysacadSchedule schedule = schedule(status);
        schedule.setSendingBuilding(5);
        schedule.setSendingRoom(229);
        schedule.setAttempts(3);
        schedule.setNextAttemptAt(EARLIER);
        schedule.setLastError("timeout");
        return schedule;
    }

    @ParameterizedTest
    @CsvSource(value = {"null,229", "5,null", "null,null"}, nullValues = "null")
    @DisplayName("lectura nula: NO_CHANGE y no muta nada")
    void nullReadingDoesNothing(Integer building, Integer room) {
        SysacadSchedule schedule = schedule();

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, building, room, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.NO_CHANGE);
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
    }

    @Test
    @DisplayName("REVIEW: guarda la diferencia, no cambia el estado ni el conocido y devuelve NO_CHANGE")
    void reviewRecordsDifferenceWithoutChangingState() {
        SysacadSchedule schedule = schedule(SysacadScheduleStatus.REVIEW);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.NO_CHANGE);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
        assertThat(schedule.getDifferentBuilding()).isEqualTo(5);
        assertThat(schedule.getDifferentRoom()).isEqualTo(229);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(NOW);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
    }

    @Test
    @DisplayName("REVIEW: la misma diferencia no pisa la fecha de detección")
    void reviewKeepsDetectionDateForSameDifference() {
        SysacadSchedule schedule = schedule(SysacadScheduleStatus.REVIEW);
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(EARLIER);
    }

    @Test
    @DisplayName("REVIEW: otra diferencia reemplaza la guardada y renueva la fecha")
    void reviewReplacesDifferentValueAndDate() {
        SysacadSchedule schedule = schedule(SysacadScheduleStatus.REVIEW);
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 230, NOW);

        assertThat(schedule.getDifferentRoom()).isEqualTo(230);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("REVIEW: si la vista vuelve al valor conocido limpia la diferencia")
    void reviewClearsDifferenceWhenViewReturnsToKnown() {
        SysacadSchedule schedule = schedule(SysacadScheduleStatus.REVIEW);
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 228, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.NO_CHANGE);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.REVIEW);
        assertThat(schedule.getDifferentBuilding()).isNull();
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(schedule.getDifferenceDetectedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(SysacadWriteMode.class)
    @DisplayName("igual al conocido: limpia la diferencia (eco de un envío ignorado) y devuelve NO_CHANGE")
    void sameAsKnownClearsDifference(SysacadWriteMode mode) {
        SysacadSchedule schedule = schedule();
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        ReconcileOutcome outcome = reconcilerIn(mode).reconcile(schedule, 5, 228, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.NO_CHANGE);
        assertThat(schedule.getDifferentBuilding()).isNull();
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(schedule.getDifferenceDetectedAt()).isNull();
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
    }

    @ParameterizedTest
    @EnumSource(value = SysacadScheduleStatus.class, names = {"PENDING", "SENDING"})
    @DisplayName("envío reciente en PENDING o SENDING: SEND_CONFIRMED, actualiza el conocido, deja la base y limpia el envío")
    void recentSendConfirmsPendingOrSending(SysacadScheduleStatus status) {
        SysacadSchedule schedule = sendingTo229(status);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.SEND_CONFIRMED);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(229);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
        assertThat(schedule.getSendingBuilding()).isNull();
        assertThat(schedule.getSendingRoom()).isNull();
        assertThat(schedule.getAttempts()).isZero();
        assertThat(schedule.getNextAttemptAt()).isNull();
        assertThat(schedule.getLastError()).isNull();
        assertThat(schedule.getDifferentRoom()).isNull();
    }

    @Test
    @DisplayName("envío reciente en modo ON nunca cuenta como cambio directo, ni con una diferencia igual ya guardada")
    void recentSendIsNeverDirectChangeInOn() {
        SysacadSchedule schedule = sendingTo229(SysacadScheduleStatus.SENDING);
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.SEND_CONFIRMED);
        assertThat(outcome).isNotEqualTo(ReconcileOutcome.DIRECT_CHANGE);
        assertThat(schedule.getDifferentRoom()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF", "DRY_RUN"})
    @DisplayName("sin escritura activa: EXTERNAL_CHANGE_APPLIED y actualiza el conocido y la base")
    void writeNotOnAppliesExternalChange(SysacadWriteMode mode) {
        SysacadSchedule schedule = schedule();
        schedule.setDifferentBuilding(6);
        schedule.setDifferentRoom(300);
        schedule.setDifferenceDetectedAt(EARLIER);

        ReconcileOutcome outcome = reconcilerIn(mode).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.EXTERNAL_CHANGE_APPLIED);
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(229);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(229);
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(schedule.getDifferenceDetectedAt()).isNull();
    }

    @Test
    @DisplayName("ON, primera lectura distinta: DIFFERENCE_RECORDED, conocido y base intactos")
    void writeOnFirstDifferentReadingRecordsDifference() {
        SysacadSchedule schedule = schedule();

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(schedule.getDifferentBuilding()).isEqualTo(5);
        assertThat(schedule.getDifferentRoom()).isEqualTo(229);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(NOW);
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
    }

    @Test
    @DisplayName("ON, otra diferencia distinta a la guardada: la reemplaza y sigue DIFFERENCE_RECORDED")
    void writeOnOtherDifferenceReplacesStoredOne() {
        SysacadSchedule schedule = schedule();
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 230, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(schedule.getDifferentRoom()).isEqualTo(230);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(NOW);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
    }

    @Test
    @DisplayName("ON, misma diferencia que la guardada: DIRECT_CHANGE, conocido y base intactos")
    void writeOnSameDifferenceIsDirectChange() {
        SysacadSchedule schedule = schedule();
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(EARLIER);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIRECT_CHANGE);
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(228);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(EARLIER);
    }

    @Test
    @DisplayName("el mismo número de aula con otro edificio cuenta como una aula distinta")
    void sameRoomNumberInOtherBuildingIsDifferent() {
        SysacadSchedule schedule = schedule();

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 6, 228, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(schedule.getDifferentBuilding()).isEqualTo(6);
        assertThat(schedule.getDifferentRoom()).isEqualTo(228);
    }

    @Test
    @DisplayName("el mismo número de aula con otro edificio no coincide con el envío reciente")
    void sameRoomNumberInOtherBuildingIsNotRecentSend() {
        SysacadSchedule schedule = sendingTo229(SysacadScheduleStatus.PENDING);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 6, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(schedule.getStatus()).isEqualTo(SysacadScheduleStatus.PENDING);
        assertThat(schedule.getSendingRoom()).isEqualTo(229);
    }

    @ParameterizedTest
    @CsvSource({"999", "0"})
    @DisplayName("las aulas 999 y 0 se comparan como cualquier otro valor")
    void sentinelRoomsAreComparedLikeAnyValue(int room) {
        SysacadSchedule different = schedule();
        ReconcileOutcome asDifference = reconcilerIn(SysacadWriteMode.ON).reconcile(different, 5, room, NOW);

        SysacadSchedule sameAsKnown = schedule();
        sameAsKnown.setKnownRoom(room);
        ReconcileOutcome asSame = reconcilerIn(SysacadWriteMode.ON).reconcile(sameAsKnown, 5, room, NOW);

        assertThat(asDifference).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(different.getDifferentRoom()).isEqualTo(room);
        assertThat(asSame).isEqualTo(ReconcileOutcome.NO_CHANGE);
    }

    @Test
    @DisplayName("ON: un SEND_INTENT reciente en el registro confirma aunque el horario ya no tenga el envío en curso")
    void loggedRecentIntentConfirmsReplacedSend() {
        SysacadScheduleLogRepository logRepository = mock(SysacadScheduleLogRepository.class);
        when(logRepository.existsRecentIntent(eq(7L), eq(5), eq(229), any())).thenReturn(true);
        SysacadSchedule withId = SysacadSchedule.builder().id(7L)
                .year(2026).specialty(5).plan(2008).subject(115).commission(7).dayOfWeek(1).semester(1)
                .knownBuilding(5).knownRoom(228).status(SysacadScheduleStatus.PENDING)
                .sendingBuilding(5).sendingRoom(230)
                .build();

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON, logRepository).reconcile(withId, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.SEND_CONFIRMED);
        assertThat(withId.getKnownRoom()).isEqualTo(229);
        assertThat(withId.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
    }

    private static SysacadSchedule withDifferenceDetectedAt(Instant detectedAt) {
        SysacadSchedule schedule = schedule();
        schedule.setDifferentBuilding(5);
        schedule.setDifferentRoom(229);
        schedule.setDifferenceDetectedAt(detectedAt);
        return schedule;
    }

    @Test
    @DisplayName("ON: la diferencia detectada exactamente un jobDelay antes dispara el cambio directo")
    void differenceExactlyJobDelayOldIsDirectChange() {
        Instant detectedAt = NOW.minus(Duration.ofMinutes(5));
        SysacadSchedule schedule = withDifferenceDetectedAt(detectedAt);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIRECT_CHANGE);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(detectedAt);
    }

    @Test
    @DisplayName("ON: a un segundo de cumplir el jobDelay sigue DIFFERENCE_RECORDED y no toca la fecha")
    void differenceOneSecondShortOfJobDelayIsStillRecorded() {
        Instant detectedAt = NOW.minus(Duration.ofMinutes(5)).plusSeconds(1);
        SysacadSchedule schedule = withDifferenceDetectedAt(detectedAt);

        ReconcileOutcome outcome = reconcilerIn(SysacadWriteMode.ON).reconcile(schedule, 5, 229, NOW);

        assertThat(outcome).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(detectedAt);
        assertThat(schedule.getKnownRoom()).isEqualTo(228);
    }

    @Test
    @DisplayName("ON: la lectura horaria y la previa al envío a segundos de distancia no son un cambio directo")
    void readingsSecondsApartAreNotDirectChange() {
        SysacadViewReconciler reconciler = reconcilerIn(SysacadWriteMode.ON);
        SysacadSchedule schedule = schedule();

        ReconcileOutcome hourly = reconciler.reconcile(schedule, 5, 229, NOW);
        ReconcileOutcome preSend = reconciler.reconcile(schedule, 5, 229, NOW.plusSeconds(20));

        assertThat(hourly).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(preSend).isEqualTo(ReconcileOutcome.DIFFERENCE_RECORDED);
        assertThat(schedule.getDifferenceDetectedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("ON: una lectura posterior al jobDelay de la primera confirma el cambio directo")
    void laterReadingAfterJobDelayConfirmsDirectChange() {
        SysacadViewReconciler reconciler = reconcilerIn(SysacadWriteMode.ON);
        SysacadSchedule schedule = schedule();

        reconciler.reconcile(schedule, 5, 229, NOW);
        ReconcileOutcome later = reconciler.reconcile(schedule, 5, 229, NOW.plus(Duration.ofMinutes(5)));

        assertThat(later).isEqualTo(ReconcileOutcome.DIRECT_CHANGE);
    }

    @Test
    @DisplayName("envío reciente con id de horario: deja una fila SEND_CONFIRMED con resultado CONFIRMED_BY_READ")
    void recentSendWritesConfirmedByReadRow() {
        SysacadScheduleLogRepository logRepository = mock(SysacadScheduleLogRepository.class);
        SysacadSchedule schedule = sendingTo229(SysacadScheduleStatus.SENDING);
        schedule.setId(11L);

        reconcilerIn(SysacadWriteMode.ON, logRepository).reconcile(schedule, 5, 229, NOW);

        ArgumentCaptor<SysacadScheduleLogEntry> captor = ArgumentCaptor.forClass(SysacadScheduleLogEntry.class);
        verify(logRepository).save(captor.capture());
        SysacadScheduleLogEntry row = captor.getValue();
        assertThat(row.getType()).isEqualTo(ScheduleLogType.SEND_CONFIRMED);
        assertThat(row.getResult()).isEqualTo("CONFIRMED_BY_READ");
        assertThat(row.getScheduleId()).isEqualTo(11L);
        assertThat(row.getOccurredAt()).isEqualTo(NOW);
        assertThat(row.getPreviousRoom()).isEqualTo(228);
        assertThat(row.getNewRoom()).isEqualTo(229);
    }
}
