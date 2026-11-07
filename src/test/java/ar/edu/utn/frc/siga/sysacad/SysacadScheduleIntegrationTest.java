package ar.edu.utn.frc.siga.sysacad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.BaseRoom;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.ResolvedScheduleRow;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.ScheduleKey;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("sysacad_horario (integración, flag encendido)")
@TestPropertySource(properties = {"siga.sysacad.enabled=true", "siga.sysacad.write.mode=ON"})
class SysacadScheduleIntegrationTest extends AbstractIntegrationTest {

    private static final int YEAR = 2097;

    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SysacadScheduleRepository repository;
    @Autowired
    private SysacadScheduleLoader loader;

    @BeforeEach
    @AfterEach
    void cleanTestRows() {
        jdbc.update("DELETE FROM sysacad_horario WHERE anio = ?", YEAR);
    }

    private void insert(int day, int semester, String status) {
        jdbc.update("INSERT INTO sysacad_horario (anio, especialidad, plan, materia, comision, dia, cuatrimestre, "
                + "estado) VALUES (?, 1, 2008, 115, 7, ?, ?, ?)", YEAR, day, semester, status);
    }

    @Test
    @DisplayName("V56 aplica: la tabla, la clave UNIQUE y los CHECK existen, y la entidad pasa ddl-auto validate")
    void migrationCreatesTableAndConstraints() {
        List<String> constraints = jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'sysacad_horario'::regclass", String.class);

        assertThat(constraints).contains("uq_sysacad_horario_clave", "chk_sysacad_horario_estado",
                "chk_sysacad_horario_cuatrimestre", "chk_sysacad_horario_dia");
        assertThat(repository.count()).isNotNegative();
    }

    @Test
    @DisplayName("La clave UNIQUE rechaza un segundo horario con la misma clave")
    void uniqueKeyRejectsDuplicate() {
        insert(1, 1, "SYNCED");

        assertThatThrownBy(() -> insert(1, 1, "SYNCED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_sysacad_horario_clave");
    }

    @Test
    @DisplayName("La misma clave con otro día o cuatrimestre sí se acepta")
    void uniqueKeyAllowsDifferentDayOrSemester() {
        insert(1, 1, "SYNCED");
        insert(2, 1, "SYNCED");
        insert(1, 2, "SYNCED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sysacad_horario WHERE anio = ?", Integer.class, YEAR))
                .isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    @DisplayName("El CHECK de dia rechaza valores fuera de 1..7")
    void checkRejectsInvalidDay(int day) {
        assertThatThrownBy(() -> insert(day, 1, "SYNCED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_sysacad_horario_dia");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3})
    @DisplayName("El CHECK de cuatrimestre rechaza valores fuera de 0..2")
    void checkRejectsInvalidSemester(int semester) {
        assertThatThrownBy(() -> insert(1, semester, "SYNCED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_sysacad_horario_cuatrimestre");
    }

    @Test
    @DisplayName("El CHECK de estado rechaza un valor fuera del enum")
    void checkRejectsInvalidStatus() {
        assertThatThrownBy(() -> insert(1, 1, "BOGUS"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_sysacad_horario_estado");
    }

    @Test
    @DisplayName("Una FK a un evento inexistente se rechaza")
    void foreignKeyRejectsUnknownEvent() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sysacad_horario (anio, especialidad, plan, materia, "
                + "comision, dia, cuatrimestre, id_evento_1) VALUES (?, 1, 2008, 115, 7, 1, 1, ?)",
                YEAR, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_sysacad_horario_evento_1");
    }

    @Test
    @DisplayName("load persiste el horario y lo relee con sus valores y los defaults de la base")
    void loadPersistsAndRereadsSchedule() {
        SysacadAllocationDto row = new SysacadAllocationDto("1K1", 115, 1, 2008, DayOfWeek.TUESDAY,
                LocalTime.of(10, 30), 135, LocalTime.of(12, 45), 7, 1, 999, 5);

        loader.load(List.of(new ResolvedScheduleRow(row, YEAR, Set.of(), NotSendableReason.COMMISSION_NOT_RESOLVED)));

        SysacadSchedule saved = repository.findAll().stream().filter(s -> s.getYear() == YEAR).findFirst()
                .orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSpecialty()).isEqualTo(1);
        assertThat(saved.getPlan()).isEqualTo(2008);
        assertThat(saved.getSubject()).isEqualTo(115);
        assertThat(saved.getCommission()).isEqualTo(7);
        assertThat(saved.getDayOfWeek()).isEqualTo(2);
        assertThat(saved.getSemester()).isEqualTo(1);
        assertThat(saved.getCourseCode()).isEqualTo("1K1");
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 45));
        assertThat(saved.getDurationMinutes()).isEqualTo(135);
        assertThat(saved.getKnownBuilding()).isEqualTo(5);
        assertThat(saved.getKnownRoom()).isEqualTo(999);
        assertThat(saved.getBaseBuilding()).isEqualTo(5);
        assertThat(saved.getBaseRoom()).isEqualTo(999);
        assertThat(saved.isSendable()).isFalse();
        assertThat(saved.getNotSendableReason()).isEqualTo(NotSendableReason.COMMISSION_NOT_RESOLVED);
        assertThat(saved.isPresentInView()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(SysacadScheduleStatus.SYNCED);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getLastReadAt()).isNotNull();
        assertThat(saved.getEventId1()).isNull();
    }

    @Test
    @DisplayName("load sobre un horario ya persistido, con escritura ON, no duplica la fila ni pisa conocido y base, y guarda la diferencia")
    void loadTwiceUpdatesInPlace() {
        SysacadAllocationDto first = new SysacadAllocationDto("1K1", 115, 1, 2008, DayOfWeek.TUESDAY,
                LocalTime.of(10, 30), 135, LocalTime.of(12, 45), 7, 1, 200, 5);
        SysacadAllocationDto second = new SysacadAllocationDto("1K1", 115, 1, 2008, DayOfWeek.TUESDAY,
                LocalTime.of(10, 30), 135, LocalTime.of(12, 45), 7, 1, 201, 6);

        loader.load(List.of(new ResolvedScheduleRow(first, YEAR, Set.of(), null)));
        loader.load(List.of(new ResolvedScheduleRow(second, YEAR, Set.of(), null)));

        List<SysacadSchedule> rows = repository.findAll().stream().filter(s -> s.getYear() == YEAR).toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getKnownRoom()).isEqualTo(200);
        assertThat(rows.getFirst().getBaseRoom()).isEqualTo(200);
        assertThat(rows.getFirst().getDifferentBuilding()).isEqualTo(6);
        assertThat(rows.getFirst().getDifferentRoom()).isEqualTo(201);
        assertThat(rows.getFirst().getDifferenceDetectedAt()).isNotNull();
        assertThat(rows.getFirst().getVersion()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("findBaseRooms devuelve solo los horarios con aula base completa")
    void findBaseRoomsOmitsSchedulesWithoutBase() {
        insert(1, 1, "SYNCED");
        jdbc.update("UPDATE sysacad_horario SET edificio_base = 5, aula_base = 228 WHERE anio = ? AND dia = 1", YEAR);
        insert(2, 1, "SYNCED");
        insert(3, 1, "SYNCED");
        jdbc.update("UPDATE sysacad_horario SET edificio_base = 5 WHERE anio = ? AND dia = 3", YEAR);

        Map<ScheduleKey, BaseRoom> bases = loader.findBaseRooms();

        assertThat(bases).containsEntry(new ScheduleKey(YEAR, 1, 2008, 115, 7, 1, 1), new BaseRoom(5, 228));
        assertThat(bases).doesNotContainKeys(new ScheduleKey(YEAR, 1, 2008, 115, 7, 2, 1),
                new ScheduleKey(YEAR, 1, 2008, 115, 7, 3, 1));
    }
}
