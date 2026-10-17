package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedConflictDto;
import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static ar.edu.utn.frc.siga.allocation.service.impl.UnallocatedConflictTestFixtures.recurring;
import static ar.edu.utn.frc.siga.allocation.service.impl.UnallocatedConflictTestFixtures.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UnallocatedConflictComparator")
class UnallocatedConflictComparatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    private static UnallocatedConflictDto rec(long id, String subject, DayOfWeek day) {
        return new UnallocatedConflictDto(recurring(id, subject, "Esp", "1K1", day, LocalTime.of(8, 0), 10));
    }

    private static UnallocatedConflictDto uni(long id, LocalDate date) {
        return new UnallocatedConflictDto(unique(id, "M", "Esp", "1K1", date, LocalTime.of(8, 0), 10));
    }

    private static List<Long> sorted(List<UnallocatedConflictDto> items, Sort sort) {
        List<UnallocatedConflictDto> copy = new ArrayList<>(items);
        copy.sort(UnallocatedConflictComparator.of(sort));
        return copy.stream().map(c -> c.event().id()).toList();
    }

    @Test
    @DisplayName("sin sort desempata por id del evento")
    void sinSortPorId() {
        var items = List.of(rec(3, "A", DayOfWeek.MONDAY), rec(1, "A", DayOfWeek.MONDAY), rec(2, "A", DayOfWeek.MONDAY));

        assertThat(sorted(items, Sort.unsorted())).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("dayOfWeek asc: a igual día recurrentes primero y únicos por fecha ascendente")
    void dayOfWeekAscRecurrentesAntesQueUnicos() {
        var items = List.of(
                uni(1, MONDAY.plusWeeks(2)), uni(2, MONDAY), rec(3, "A", DayOfWeek.MONDAY), rec(4, "A", DayOfWeek.SUNDAY));

        assertThat(sorted(items, Sort.by("dayOfWeek"))).containsExactly(3L, 2L, 1L, 4L);
    }

    @Test
    @DisplayName("dayOfWeek desc invierte el orden completo, incluidos los únicos por fecha")
    void dayOfWeekDesc() {
        var items = List.of(
                uni(1, MONDAY.plusWeeks(2)), uni(2, MONDAY), rec(3, "A", DayOfWeek.MONDAY), rec(4, "A", DayOfWeek.SUNDAY));

        assertThat(sorted(items, Sort.by(Sort.Direction.DESC, "dayOfWeek"))).containsExactly(4L, 1L, 2L, 3L);
    }

    @Test
    @DisplayName("sort múltiple aplica el segundo criterio ante empate")
    void sortMultiple() {
        var items = List.of(rec(1, "B", DayOfWeek.MONDAY), rec(2, "A", DayOfWeek.TUESDAY), rec(3, "A", DayOfWeek.MONDAY));

        assertThat(sorted(items, Sort.by("subject", "dayOfWeek"))).containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("validate acepta las cinco propiedades permitidas")
    void validateAcepta() {
        assertThatCode(() -> UnallocatedConflictComparator.validate(
                Sort.by("subject", "dayOfWeek", "startTime", "enrolled", "commissionCode"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate rechaza una propiedad desconocida con 400 y la lista de válidas")
    void validateRechaza() {
        assertThatThrownBy(() -> UnallocatedConflictComparator.validate(Sort.by("subject", "id")))
                .isInstanceOfSatisfying(InvalidSortPropertyException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("'id'");
                });
    }

    @Test
    @DisplayName("of rechaza una propiedad desconocida")
    void ofRechaza() {
        assertThatThrownBy(() -> UnallocatedConflictComparator.of(Sort.by("nope")))
                .isInstanceOf(InvalidSortPropertyException.class);
    }
}
