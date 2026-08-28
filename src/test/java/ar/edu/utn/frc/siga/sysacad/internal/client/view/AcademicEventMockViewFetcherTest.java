package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawAcademicEventMock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AcademicEventMockViewFetcher")
class AcademicEventMockViewFetcherTest {

    private final AcademicEventMockViewFetcher fetcher = new AcademicEventMockViewFetcher(
            new ClassPathResource("sysacad/mock/materia_comision.json"), new ObjectMapper());

    @Test
    @DisplayName("fetch: aplana el horario semanal anidado a una fila por ocurrencia")
    void fetchFlattensNestedSchedule() {
        List<RawAcademicEventMock> rows = fetcher.fetch();

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.curso()).isNotBlank();
            assertThat(row.materia()).isNotNull();
            assertThat(row.dia()).isBetween(1, 6);
            assertThat(row.horaComienzo()).matches("\\d{2}:\\d{2}");
            assertThat(row.duracion()).isPositive();
            assertThat(row.horarioCuatrimestre()).isIn(Set.of(0, 1, 2));
        });
    }

    @Test
    @DisplayName("fetch: un (curso, materia) con más de una clase semanal produce más de una fila")
    void fetchProducesMoreThanOneRowForMultiOccurrenceCourse() {
        List<RawAcademicEventMock> rows = fetcher.fetch();

        long distinctCourseSubjectPairs = rows.stream()
                .map(row -> row.curso() + "|" + row.materia())
                .distinct()
                .count();

        assertThat(rows.size()).isGreaterThan((int) distinctCourseSubjectPairs);
    }
}
