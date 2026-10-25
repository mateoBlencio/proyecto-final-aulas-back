package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomDetailsUpdateDto;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomResourceRequestDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the catalog entities audited with Envers leave the expected {@code _aud} rows when written through
 * their services, and that a Sysacad sync with no changes leaves none. Commits are real (no
 * {@code @Transactional}): Envers needs them.
 */
@Import(IntegrationTestData.class)
@DisplayName("Auditoría de catálogos de space y academic (integración)")
class CatalogAuditIntegrationTest extends AbstractIntegrationTest {

    private static final short ADD = 0;
    private static final short MOD = 1;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private BuildingService buildingService;
    @Autowired
    private HolidayService holidayService;
    @Autowired
    private ClassroomService classroomService;

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long countWhere(String table, String column, Object value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }

    @Test
    @DisplayName("syncBuildings repetido con los mismos comandos no agrega filas a edificio_aud ni revisiones")
    void syncBuildings_sameCommandsTwice_secondRunAuditsNothing() {
        int codeA = 800_000 + (int) IntegrationTestData.nextSeq();
        int codeB = codeA + 1;
        List<BuildingSyncCommand> commands = List.of(
                new BuildingSyncCommand(codeA, "Edificio sync A " + codeA),
                new BuildingSyncCommand(codeB, "Edificio sync B " + codeB));

        assertThat(buildingService.syncBuildings(commands, 0)).isPositive();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM edificio_aud a JOIN edificio e ON e.id_edificio = a.id_edificio "
                        + "WHERE e.codigo_edificio IN (?, ?) AND a.revtype = ?", Long.class, codeA, codeB, ADD))
                .isEqualTo(2L);
        long auditRowsAfterFirst = count("edificio_aud");
        long revisionsAfterFirst = count("revinfo");

        buildingService.syncBuildings(commands, 0);

        assertThat(count("edificio_aud")).isEqualTo(auditRowsAfterFirst);
        assertThat(count("revinfo")).isEqualTo(revisionsAfterFirst);
    }

    @Test
    @DisplayName("syncHolidays repetido con los mismos comandos no agrega filas a feriado_aud ni revisiones")
    void syncHolidays_sameCommandsTwice_secondRunAuditsNothing() {
        LocalDate first = LocalDate.of(2091, 3, 5);
        LocalDate second = LocalDate.of(2091, 3, 6);
        List<HolidaySyncCommand> commands = List.of(
                new HolidaySyncCommand(first, "Feriado de prueba uno"),
                new HolidaySyncCommand(second, "Feriado de prueba dos"));

        holidayService.syncHolidays(commands);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM feriado_aud WHERE fecha IN (?, ?) AND revtype = ?", Long.class,
                first, second, ADD)).isEqualTo(2L);
        long auditRowsAfterFirst = count("feriado_aud");
        long revisionsAfterFirst = count("revinfo");

        holidayService.syncHolidays(commands);

        assertThat(count("feriado_aud")).isEqualTo(auditRowsAfterFirst);
        assertThat(count("revinfo")).isEqualTo(revisionsAfterFirst);
    }

    @Test
    @DisplayName("deactivate de un aula deja un MOD en aula_aud con eliminado_en y una revisión con la descripción de la operación")
    void deactivateClassroom_leavesModWithDeletionTimestampAndOperation() {
        Classroom classroom = testData.aula(testData.edificio());
        long addRows = countWhere("aula_aud", "id_aula", classroom.getId());

        asFixtureUser(() -> {
            classroomService.deactivate(classroom.getId());
        });

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.revtype, a.eliminado_en, r.operacion_id, r.descripcion FROM aula_aud a "
                        + "JOIN revinfo r ON r.rev = a.rev WHERE a.id_aula = ? ORDER BY a.rev DESC",
                classroom.getId());
        assertThat(rows).hasSize((int) addRows + 1);
        Map<String, Object> latest = rows.get(0);
        assertThat(((Number) latest.get("revtype")).shortValue()).isEqualTo(MOD);
        assertThat(latest.get("eliminado_en")).isNotNull();
        assertThat(latest.get("operacion_id")).isNotNull();
        assertThat(latest.get("descripcion")).isEqualTo("Baja de aula");
    }

    @Test
    @DisplayName("updateDetails que solo cambia la cantidad de un recurso deja un MOD en aula_recurso_aud y nada en aula_permiso_aud")
    void updateDetails_onlyResourceQuantity_auditsOnlyTheResource() {
        Building building = testData.edificio();
        ClassroomType type = testData.tipoAulaPorDefecto();
        Classroom classroom = testData.aula(building, type, 40);
        ResourceType resourceType = testData.tipoRecurso("Enchufes-IT-" + IntegrationTestData.nextSeq(),
                ResourceValueKind.COUNT);
        var resource = testData.recursoDeAula(classroom, resourceType, 2);
        long resourceMods = countWhere("aula_recurso_aud", "revtype", MOD);
        long permissionRows = count("aula_permiso_aud");

        asFixtureUser(() -> {
            classroomService.updateDetails(classroom.getId(), new ClassroomDetailsUpdateDto(
                    type.getId(), classroom.getObservations(), classroom.getPermissionMode(), List.of(),
                    List.of(new ClassroomResourceRequestDto(resourceType.getId(), 5))));
        });

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM aula_recurso_aud WHERE id_aula_recurso = ? AND revtype = ? AND cantidad = 5",
                Long.class, resource.getId(), MOD)).isEqualTo(1L);
        assertThat(countWhere("aula_recurso_aud", "revtype", MOD)).isEqualTo(resourceMods + 1);
        assertThat(count("aula_permiso_aud")).isEqualTo(permissionRows);
    }
}
