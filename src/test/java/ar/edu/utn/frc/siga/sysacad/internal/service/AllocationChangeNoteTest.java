package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.internal.service.AllocationChangeNote.Line;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AllocationChangeNote: texto del mensaje por cambio de aula")
class AllocationChangeNoteTest {

    private static final LocalTime START = LocalTime.of(13, 15);

    @Test
    @DisplayName("asignación, cambio y liberación se describen distinto, ordenados por fecha")
    void describesEachKindOfChange() {
        String note = AllocationChangeNote.compose(List.of(
                new Line(LocalDate.of(2026, 11, 17), START, 220, null),
                new Line(LocalDate.of(2026, 11, 10), START, 220, 305),
                new Line(LocalDate.of(2026, 11, 3), START, null, 220)));

        assertThat(note).isEqualTo(
                "Cambio de aula: 03/11 13:15 aula 220; 10/11 13:15 aula 220>305; 17/11 13:15 sin aula");
    }

    @Test
    @DisplayName("si no entra en 150 caracteres, corta y resume el resto con 'y N más'")
    void truncatesWithOmittedCount() {
        List<Line> lines = IntStream.rangeClosed(1, 20)
                .mapToObj(day -> new Line(LocalDate.of(2026, 11, day), START, 220, 305))
                .toList();

        String note = AllocationChangeNote.compose(lines);

        assertThat(note).hasSizeLessThanOrEqualTo(AllocationChangeNote.MAX_LENGTH)
                .startsWith("Cambio de aula: 01/11 13:15 aula 220>305; 02/11")
                .matches(".* y \\d+ más$");
    }
}
