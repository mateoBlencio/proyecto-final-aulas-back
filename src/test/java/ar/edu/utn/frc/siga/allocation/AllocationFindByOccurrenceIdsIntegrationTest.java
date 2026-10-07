package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the Sysacad sync error: PostgreSQL rejects a query with more than 65,535 parameters
 * ("PreparedStatement can have at most 65,535 parameters"). Occurrences and allocations are inserted with
 * bulk SQL (generate_series) because 70,000 {@code save} calls would take minutes.
 */
@Import(IntegrationTestData.class)
@DisplayName("AllocationRepository.findByOccurrenceIdIn (integración, límite de parámetros de PostgreSQL)")
class AllocationFindByOccurrenceIdsIntegrationTest extends AbstractIntegrationTest {

    private static final int OCCURRENCES = 70_000;
    private static final long ID_OFFSET = 10_000_000L;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("más de 65.535 ids no lanza excepción y devuelve exactamente las asignaciones existentes")
    void moreThanParameterLimit_returnsExactlyExistingAllocations() {
        long firstId = seedOccurrencesWithEveryThirdAllocated();
        List<Long> allIds = LongStream.range(firstId, firstId + OCCURRENCES).boxed().toList();
        List<Long> expectedAllocated = allIds.stream().filter(id -> id % 3 == 0).toList();
        assertThat(allIds.size()).isGreaterThan(65_535);
        assertThat(expectedAllocated).isNotEmpty().hasSizeLessThan(allIds.size());

        List<Allocation> result = allocationRepository.findByOccurrenceIdIn(allIds);

        assertThat(result).extracting(Allocation::getOccurrenceId)
                .containsExactlyInAnyOrderElementsOf(expectedAllocated);
    }

    @Test
    @DisplayName("ids repetidos entre tandas devuelven cada asignación una sola vez")
    void idsRepeatedAcrossChunks_returnEachAllocationOnce() {
        long firstId = seedOccurrencesWithEveryThirdAllocated();
        long allocatedId = firstId;
        while (allocatedId % 3 != 0) {
            allocatedId++;
        }
        List<Long> ids = new ArrayList<>(LongStream.range(firstId, firstId + OCCURRENCES).boxed().toList());
        // The same allocated id sits in the first chunk and again in a later one (position > 10,000).
        ids.set(OCCURRENCES - 1, allocatedId);
        List<Long> expectedAllocated = ids.stream().distinct().filter(id -> id % 3 == 0).toList();

        List<Allocation> result = allocationRepository.findByOccurrenceIdIn(ids);

        assertThat(result).extracting(Allocation::getOccurrenceId)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expectedAllocated);
    }

    /** Inserts {@code OCCURRENCES} consecutive occurrences, one in three with an allocation. Returns the first id. */
    private long seedOccurrencesWithEveryThirdAllocated() {
        Classroom classroom = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(30);
        var sc = testData.materiaYComision();
        Long eventId = academicEventService.createRecurringEvent(new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, date.getDayOfWeek(), date, date,
                sc.subjectId(), sc.commissionId())).id();

        // Explicit ids far above what Hibernate assigns: the Hibernate sequence and the column default
        // do not share a counter.
        long firstId = jdbcTemplate.queryForObject(
                "SELECT GREATEST(COALESCE((SELECT MAX(id_ocurrencia) FROM ocurrencia), 0),"
                        + " COALESCE((SELECT MAX(id_asignacion) FROM asignacion_aula), 0)) + ?",
                Long.class, ID_OFFSET);
        jdbcTemplate.update(
                "INSERT INTO ocurrencia (id_ocurrencia, id_evento_academico, fecha, orden_aula)"
                        + " SELECT g, ?, ?, g FROM generate_series(?::bigint, ?::bigint) g",
                eventId, java.sql.Date.valueOf(date), firstId, firstId + OCCURRENCES - 1);
        jdbcTemplate.update(
                "INSERT INTO asignacion_aula (id_asignacion, id_ocurrencia, id_aula, origen)"
                        + " SELECT g, g, ?, 'SYSACAD' FROM generate_series(?::bigint, ?::bigint) g WHERE g % 3 = 0",
                classroom.getId(), firstId, firstId + OCCURRENCES - 1);
        return firstId;
    }
}
