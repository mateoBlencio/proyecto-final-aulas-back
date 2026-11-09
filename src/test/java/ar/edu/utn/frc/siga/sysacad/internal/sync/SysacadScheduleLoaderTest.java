package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.SysacadTestSettings;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.BaseRoom;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.LoadResult;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.ResolvedScheduleRow;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.ScheduleKey;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadScheduleLoader")
class SysacadScheduleLoaderTest {

    private static final int YEAR = 2026;
    private static final int SPECIALTY = 5;
    private static final int PLAN = 2008;
    private static final int SUBJECT = 115;
    private static final int COMMISSION = 7;
    private static final int SEMESTER = 1;

    @Mock
    private SysacadScheduleRepository repository;

    private SysacadScheduleLoader loader;

    @BeforeEach
    void setUp() {
        loader = loaderIn(SysacadWriteMode.OFF);
    }

    private SysacadScheduleLoader loaderIn(SysacadWriteMode mode) {
        SysacadProperties properties = new SysacadProperties();
        SysacadRuntimeSettings runtimeSettings = SysacadTestSettings.with(mode);
        var logRepository = org.mockito.Mockito.mock(
                ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository.class);
        return new SysacadScheduleLoader(repository,
                new SysacadViewReconciler(runtimeSettings, new RecentSendLookup(logRepository, properties),
                        logRepository),
                runtimeSettings);
    }

    private static SysacadAllocationDto dto(LocalTime start, LocalTime end, Integer duration, Integer building,
            Integer room, String course) {
        return new SysacadAllocationDto(course, SUBJECT, SPECIALTY, PLAN, DayOfWeek.MONDAY, start, duration, end,
                COMMISSION, SEMESTER, room, building);
    }

    private static SysacadAllocationDto baseDto() {
        return dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 5, 200, "1K1");
    }

    private static ResolvedScheduleRow resolved(SysacadAllocationDto row, Long... eventIds) {
        return new ResolvedScheduleRow(row, YEAR, Set.of(eventIds), null);
    }

    private static ResolvedScheduleRow resolvedWithReason(SysacadAllocationDto row, NotSendableReason reason,
            Long... eventIds) {
        return new ResolvedScheduleRow(row, YEAR, Set.of(eventIds), reason);
    }

    private static SysacadSchedule existing() {
        return SysacadSchedule.builder()
                .year(YEAR).specialty(SPECIALTY).plan(PLAN).subject(SUBJECT).commission(COMMISSION)
                .dayOfWeek(DayOfWeek.MONDAY.getValue()).semester(SEMESTER)
                .courseCode("1K1")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 30)).durationMinutes(90)
                .knownBuilding(5).knownRoom(200).baseBuilding(5).baseRoom(200)
                .eventId1(10L)
                .build();
    }

    private List<SysacadSchedule> savedSchedules() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysacadSchedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 999, 0})
    @DisplayName("load: horario nuevo carga copia, valor conocido y aula base, incluso con aula 999 o 0")
    void newScheduleLoadsCopyKnownAndBase(int room) {
        when(repository.findAll()).thenReturn(List.of());
        SysacadAllocationDto row = dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 5, room, "1K1");

        loader.load(List.of(resolved(row, 10L)));

        SysacadSchedule saved = savedSchedules().getFirst();
        assertThat(saved.getYear()).isEqualTo(YEAR);
        assertThat(saved.getSpecialty()).isEqualTo(SPECIALTY);
        assertThat(saved.getPlan()).isEqualTo(PLAN);
        assertThat(saved.getSubject()).isEqualTo(SUBJECT);
        assertThat(saved.getCommission()).isEqualTo(COMMISSION);
        assertThat(saved.getDayOfWeek()).isEqualTo(1);
        assertThat(saved.getSemester()).isEqualTo(SEMESTER);
        assertThat(saved.getCourseCode()).isEqualTo("1K1");
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(saved.getDurationMinutes()).isEqualTo(90);
        assertThat(saved.getKnownBuilding()).isEqualTo(5);
        assertThat(saved.getKnownRoom()).isEqualTo(room);
        assertThat(saved.getBaseBuilding()).isEqualTo(5);
        assertThat(saved.getBaseRoom()).isEqualTo(room);
        assertThat(saved.getEventId1()).isEqualTo(10L);
        assertThat(saved.getEventId2()).isNull();
        assertThat(saved.isSendable()).isTrue();
        assertThat(saved.getNotSendableReason()).isNull();
        assertThat(saved.isPresentInView()).isTrue();
        assertThat(saved.getLastReadAt()).isNotNull();
    }

    @Test
    @DisplayName("load: filas idénticas de la misma clave producen un solo horario enviable")
    void identicalRowsProduceOneSendableSchedule() {
        when(repository.findAll()).thenReturn(List.of());

        loader.load(List.of(resolved(baseDto(), 10L), resolved(baseDto(), 10L)));

        List<SysacadSchedule> saved = savedSchedules();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().isSendable()).isTrue();
        assertThat(saved.getFirst().getNotSendableReason()).isNull();
        assertThat(saved.getFirst().getKnownRoom()).isEqualTo(200);
    }

    private static SysacadAllocationDto variant(String field) {
        return switch (field) {
            case "startTime" -> dto(LocalTime.of(8, 15), LocalTime.of(9, 30), 90, 5, 200, "1K1");
            case "endTime" -> dto(LocalTime.of(8, 0), LocalTime.of(10, 0), 90, 5, 200, "1K1");
            case "duration" -> dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 120, 5, 200, "1K1");
            case "building" -> dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 6, 200, "1K1");
            case "room" -> dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 5, 201, "1K1");
            case "courseCode" -> dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 5, 200, "1K2");
            default -> throw new IllegalArgumentException(field);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"startTime", "endTime", "duration", "building", "room", "courseCode"})
    @DisplayName("load: filas de la misma clave que difieren en un campo son KEY_CONFLICT, no enviable y sin valores conocidos")
    void rowsDifferingInOneFieldAreKeyConflict(String field) {
        when(repository.findAll()).thenReturn(List.of());

        loader.load(List.of(resolved(baseDto(), 10L), resolved(variant(field), 10L)));

        List<SysacadSchedule> saved = savedSchedules();
        assertThat(saved).hasSize(1);
        SysacadSchedule schedule = saved.getFirst();
        assertThat(schedule.isSendable()).isFalse();
        assertThat(schedule.getNotSendableReason()).isEqualTo(NotSendableReason.KEY_CONFLICT);
        assertThat(schedule.getKnownBuilding()).isNull();
        assertThat(schedule.getKnownRoom()).isNull();
        assertThat(schedule.getBaseBuilding()).isNull();
        assertThat(schedule.getBaseRoom()).isNull();
        assertThat(schedule.getStartTime()).isNull();
        assertThat(schedule.getEndTime()).isNull();
        assertThat(schedule.getDurationMinutes()).isNull();
        assertThat(schedule.getEventId1()).isEqualTo(10L);
    }

    @Test
    @DisplayName("load: KEY_CONFLICT sobre un horario existente no pisa hora, fin, duración, conocido ni base")
    void keyConflictDoesNotOverwriteExistingSchedule() {
        SysacadSchedule schedule = existing();
        when(repository.findAll()).thenReturn(List.of(schedule));

        loader.load(List.of(resolved(baseDto(), 10L), resolved(variant("startTime"), 10L)));

        assertThat(schedule.isSendable()).isFalse();
        assertThat(schedule.getNotSendableReason()).isEqualTo(NotSendableReason.KEY_CONFLICT);
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(schedule.getDurationMinutes()).isEqualTo(90);
        assertThat(schedule.getCourseCode()).isEqualTo("1K1");
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(200);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(200);
    }

    private static SysacadAllocationDto otherRoomRow() {
        return dto(LocalTime.of(10, 0), LocalTime.of(11, 0), 60, 6, 301, "1K9");
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF"})
    @DisplayName("load: sin escritura activa, otra aula en un horario existente actualiza hora, vínculo, conocido y base")
    void existingScheduleWithOtherRoomFollowsViewWhenWriteNotOn(SysacadWriteMode mode) {
        SysacadSchedule schedule = existing();
        when(repository.findAll()).thenReturn(List.of(schedule));

        loaderIn(mode).load(List.of(resolved(otherRoomRow(), 20L, 30L)));

        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(schedule.getDurationMinutes()).isEqualTo(60);
        assertThat(schedule.getCourseCode()).isEqualTo("1K9");
        assertThat(schedule.getEventId1()).isEqualTo(20L);
        assertThat(schedule.getEventId2()).isEqualTo(30L);
        assertThat(schedule.getKnownBuilding()).isEqualTo(6);
        assertThat(schedule.getKnownRoom()).isEqualTo(301);
        assertThat(schedule.getBaseBuilding()).isEqualTo(6);
        assertThat(schedule.getBaseRoom()).isEqualTo(301);
        assertThat(schedule.getDifferentRoom()).isNull();
        assertThat(schedule.isSendable()).isTrue();
    }

    @Test
    @DisplayName("load: con escritura ON, otra aula en un horario existente guarda la diferencia y no toca conocido ni base")
    void existingScheduleWithOtherRoomRecordsDifferenceWhenWriteOn() {
        SysacadSchedule schedule = existing();
        when(repository.findAll()).thenReturn(List.of(schedule));

        loaderIn(SysacadWriteMode.ON).load(List.of(resolved(otherRoomRow(), 20L, 30L)));

        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(schedule.getEventId1()).isEqualTo(20L);
        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(200);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(200);
        assertThat(schedule.getDifferentBuilding()).isEqualTo(6);
        assertThat(schedule.getDifferentRoom()).isEqualTo(301);
        assertThat(schedule.getDifferenceDetectedAt()).isNotNull();
    }

    @Test
    @DisplayName("load: con escritura SANDBOX, otra aula en un horario existente guarda la diferencia y no toca conocido ni base")
    void existingScheduleWithOtherRoomRecordsDifferenceWhenSandbox() {
        SysacadSchedule schedule = existing();
        when(repository.findAll()).thenReturn(List.of(schedule));

        LoadResult result = loaderIn(SysacadWriteMode.SANDBOX).load(List.of(resolved(otherRoomRow(), 20L, 30L)));

        assertThat(schedule.getKnownBuilding()).isEqualTo(5);
        assertThat(schedule.getKnownRoom()).isEqualTo(200);
        assertThat(schedule.getBaseBuilding()).isEqualTo(5);
        assertThat(schedule.getBaseRoom()).isEqualTo(200);
        assertThat(schedule.getDifferentBuilding()).isEqualTo(6);
        assertThat(schedule.getDifferentRoom()).isEqualTo(301);
        assertThat(result.eventIdsToUpdate()).isEmpty();
    }

    @Test
    @DisplayName("load: horario existente con conocido nulo que llega sin conflicto carga conocido y base")
    void existingScheduleWithNullKnownLoadsKnownAndBase() {
        SysacadSchedule schedule = existing();
        schedule.setKnownBuilding(null);
        schedule.setKnownRoom(null);
        schedule.setBaseBuilding(null);
        schedule.setBaseRoom(null);
        schedule.setSendable(false);
        schedule.setNotSendableReason(NotSendableReason.KEY_CONFLICT);
        when(repository.findAll()).thenReturn(List.of(schedule));

        loader.load(List.of(resolved(dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 6, 301, "1K1"), 10L)));

        assertThat(schedule.getKnownBuilding()).isEqualTo(6);
        assertThat(schedule.getKnownRoom()).isEqualTo(301);
        assertThat(schedule.getBaseBuilding()).isEqualTo(6);
        assertThat(schedule.getBaseRoom()).isEqualTo(301);
        assertThat(schedule.isSendable()).isTrue();
        assertThat(schedule.getNotSendableReason()).isNull();
    }

    @Test
    @DisplayName("load: tres eventos es TOO_MANY_EVENTS y vincula los dos ids menores en orden")
    void threeEventsAreTooMany() {
        when(repository.findAll()).thenReturn(List.of());

        loader.load(List.of(resolved(baseDto(), 30L), resolved(baseDto(), 20L, 10L)));

        SysacadSchedule schedule = savedSchedules().getFirst();
        assertThat(schedule.isSendable()).isFalse();
        assertThat(schedule.getNotSendableReason()).isEqualTo(NotSendableReason.TOO_MANY_EVENTS);
        assertThat(schedule.getEventId1()).isEqualTo(10L);
        assertThat(schedule.getEventId2()).isEqualTo(20L);
    }

    @Test
    @DisplayName("load: dos eventos (anual) quedan enviables con id_evento_1 menor que id_evento_2")
    void twoEventsAreOrderedAndSendable() {
        when(repository.findAll()).thenReturn(List.of());

        loader.load(List.of(resolved(baseDto(), 20L, 10L)));

        SysacadSchedule schedule = savedSchedules().getFirst();
        assertThat(schedule.getEventId1()).isEqualTo(10L);
        assertThat(schedule.getEventId2()).isEqualTo(20L);
        assertThat(schedule.isSendable()).isTrue();
    }

    @Test
    @DisplayName("load: el skipReason de una fila se propaga como motivo de no enviable")
    void skipReasonPropagates() {
        when(repository.findAll()).thenReturn(List.of());

        loader.load(List.of(resolvedWithReason(baseDto(), NotSendableReason.ROOM_UNKNOWN, 10L)));

        SysacadSchedule schedule = savedSchedules().getFirst();
        assertThat(schedule.isSendable()).isFalse();
        assertThat(schedule.getNotSendableReason()).isEqualTo(NotSendableReason.ROOM_UNKNOWN);
    }

    @Test
    @DisplayName("load: un horario no enviable vuelve a enviable si la carga siguiente viene limpia")
    void nonSendableScheduleRecoversOnCleanLoad() {
        SysacadSchedule schedule = existing();
        schedule.setSendable(false);
        schedule.setNotSendableReason(NotSendableReason.EVENT_NOT_FOUND);
        when(repository.findAll()).thenReturn(List.of(schedule));

        loader.load(List.of(resolved(baseDto(), 10L)));

        assertThat(schedule.isSendable()).isTrue();
        assertThat(schedule.getNotSendableReason()).isNull();
    }

    @Test
    @DisplayName("load: un horario ausente de la carga queda con presente_en_vista=false y el presente vuelve a true")
    void absentScheduleIsMarkedNotPresent() {
        SysacadSchedule present = existing();
        present.setPresentInView(false);
        SysacadSchedule absent = existing();
        absent.setCommission(COMMISSION + 1);
        when(repository.findAll()).thenReturn(List.of(present, absent));
        Instant before = Instant.now();

        loader.load(List.of(resolved(baseDto(), 10L)));

        assertThat(present.isPresentInView()).isTrue();
        assertThat(present.getLastReadAt()).isAfterOrEqualTo(before);
        assertThat(absent.isPresentInView()).isFalse();
        assertThat(absent.getLastReadAt()).isNull();
    }

    @Test
    @DisplayName("load: una carga vacía no marca ausente a ningún horario")
    void emptyLoadDoesNotMarkAnyoneAbsent() {
        SysacadSchedule schedule = existing();
        when(repository.findAll()).thenReturn(List.of(schedule));

        loader.load(List.of());

        assertThat(schedule.isPresentInView()).isTrue();
        verify(repository).saveAll(anyList());
    }

    // ---------- eventIdsToUpdate, cambios directos y aulas base ----------

    private static SysacadAllocationDto otherRoomSameTimeRow() {
        return dto(LocalTime.of(8, 0), LocalTime.of(9, 30), 90, 6, 301, "1K1");
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF"})
    @DisplayName("load: sin escritura activa, un horario nuevo manda sus eventos a actualizar")
    void newScheduleSendsEventsToUpdateWhenWriteNotOn(SysacadWriteMode mode) {
        when(repository.findAll()).thenReturn(List.of());

        LoadResult result = loaderIn(mode).load(List.of(resolved(baseDto(), 20L, 10L)));

        assertThat(result.eventIdsToUpdate()).containsExactly(10L, 20L);
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF"})
    @DisplayName("load: sin escritura activa, un cambio externo de aula manda sus eventos a actualizar")
    void externalChangeSendsEventsToUpdateWhenWriteNotOn(SysacadWriteMode mode) {
        when(repository.findAll()).thenReturn(List.of(existing()));

        LoadResult result = loaderIn(mode).load(List.of(resolved(otherRoomSameTimeRow(), 10L)));

        assertThat(result.eventIdsToUpdate()).containsExactly(10L);
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF"})
    @DisplayName("load: sin escritura activa, KEY_CONFLICT manda sus eventos a actualizar")
    void keyConflictSendsEventsToUpdateWhenWriteNotOn(SysacadWriteMode mode) {
        when(repository.findAll()).thenReturn(List.of(existing()));

        LoadResult result = loaderIn(mode)
                .load(List.of(resolved(baseDto(), 10L), resolved(variant("room"), 10L)));

        assertThat(result.eventIdsToUpdate()).containsExactly(10L);
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF"})
    @DisplayName("load: sin escritura activa, conocido igual a base sigue a la vista y repara una propagación fallida")
    void viewEqualToKnownAndBaseSendsEventsToUpdate(SysacadWriteMode mode) {
        when(repository.findAll()).thenReturn(List.of(existing()));

        LoadResult result = loaderIn(mode).load(List.of(resolved(baseDto(), 10L)));

        assertThat(result.eventIdsToUpdate()).containsExactly(10L);
    }

    @ParameterizedTest
    @EnumSource(value = SysacadWriteMode.class, names = {"OFF"})
    @DisplayName("load: sin escritura activa, un puntual de SIGA (conocido distinto de base) no se propaga a las clases")
    void punctualRoomSentBySigaSendsNoEventsToUpdate(SysacadWriteMode mode) {
        SysacadSchedule punctual = existing();
        punctual.setBaseBuilding(9);
        punctual.setBaseRoom(777);
        when(repository.findAll()).thenReturn(List.of(punctual));

        LoadResult result = loaderIn(mode).load(List.of(resolved(baseDto(), 10L)));

        assertThat(result.eventIdsToUpdate()).isEmpty();
    }

    @Test
    @DisplayName("load: con escritura SANDBOX nunca hay eventos a actualizar (horario nuevo, cambio de aula, KEY_CONFLICT y vista igual a la base)")
    void writeSandboxNeverSendsEventsToUpdate() {
        SysacadScheduleLoader sandboxLoader = loaderIn(SysacadWriteMode.SANDBOX);
        when(repository.findAll()).thenReturn(List.of(existing()));

        LoadResult changed = sandboxLoader.load(List.of(resolved(otherRoomSameTimeRow(), 10L)));
        LoadResult conflict = sandboxLoader.load(List.of(resolved(baseDto(), 10L), resolved(variant("room"), 10L)));
        LoadResult same = sandboxLoader.load(List.of(resolved(baseDto(), 10L)));

        when(repository.findAll()).thenReturn(List.of());
        LoadResult created = sandboxLoader.load(List.of(resolved(baseDto(), 10L)));

        assertThat(changed.eventIdsToUpdate()).isEmpty();
        assertThat(conflict.eventIdsToUpdate()).isEmpty();
        assertThat(same.eventIdsToUpdate()).isEmpty();
        assertThat(created.eventIdsToUpdate()).isEmpty();
    }

    @Test
    @DisplayName("load: con escritura ON nunca hay eventos a actualizar (horario nuevo, cambio de aula y KEY_CONFLICT)")
    void writeOnNeverSendsEventsToUpdate() {
        SysacadScheduleLoader onLoader = loaderIn(SysacadWriteMode.ON);
        when(repository.findAll()).thenReturn(List.of(existing()));

        LoadResult changed = onLoader.load(List.of(resolved(otherRoomSameTimeRow(), 10L)));
        LoadResult conflict = onLoader.load(List.of(resolved(baseDto(), 10L), resolved(variant("room"), 10L)));

        when(repository.findAll()).thenReturn(List.of());
        LoadResult created = onLoader.load(List.of(resolved(baseDto(), 10L)));

        assertThat(changed.eventIdsToUpdate()).isEmpty();
        assertThat(conflict.eventIdsToUpdate()).isEmpty();
        assertThat(created.eventIdsToUpdate()).isEmpty();
    }

    @Test
    @DisplayName("load: con escritura ON la segunda lectura de la misma diferencia lista el horario como cambio directo")
    void secondReadingOfSameDifferenceIsDirectChange() {
        SysacadSchedule schedule = SysacadSchedule.builder().id(77L)
                .year(YEAR).specialty(SPECIALTY).plan(PLAN).subject(SUBJECT).commission(COMMISSION)
                .dayOfWeek(DayOfWeek.MONDAY.getValue()).semester(SEMESTER)
                .knownBuilding(5).knownRoom(200).baseBuilding(5).baseRoom(200).eventId1(10L).build();
        when(repository.findAll()).thenReturn(List.of(schedule));
        SysacadScheduleLoader onLoader = loaderIn(SysacadWriteMode.ON);

        LoadResult first = onLoader.load(List.of(resolved(otherRoomSameTimeRow(), 10L)));
        schedule.setDifferenceDetectedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
        LoadResult second = onLoader.load(List.of(resolved(otherRoomSameTimeRow(), 10L)));

        assertThat(first.directChangeScheduleIds()).isEmpty();
        assertThat(second.directChangeScheduleIds()).containsExactly(77L);
        assertThat(schedule.getKnownRoom()).isEqualTo(200);
        assertThat(schedule.getBaseRoom()).isEqualTo(200);
    }

    @Test
    @DisplayName("findBaseRooms: devuelve la aula base por clave de horario y consulta solo los horarios con base")
    void findBaseRoomsMapsByScheduleKey() {
        SysacadSchedule withBase = existing();
        withBase.setBaseBuilding(6);
        withBase.setBaseRoom(301);
        when(repository.findByBaseBuildingIsNotNullAndBaseRoomIsNotNull()).thenReturn(List.of(withBase));

        Map<ScheduleKey, BaseRoom> bases = loader.findBaseRooms();

        assertThat(bases).containsOnly(Map.entry(
                new ScheduleKey(YEAR, SPECIALTY, PLAN, SUBJECT, COMMISSION, DayOfWeek.MONDAY.getValue(), SEMESTER),
                new BaseRoom(6, 301)));
    }
}
