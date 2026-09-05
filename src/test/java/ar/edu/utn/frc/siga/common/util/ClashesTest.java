package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Clashes")
class ClashesTest {

    private record Slot(String id, Long classroomId, LocalDate date, LocalTime startTime, LocalTime endTime)
            implements TimeSpan {
    }

    private record Hit(String a, String b) {
    }

    private static List<RoomDate> key(Slot slot) {
        return List.of(new RoomDate(slot.classroomId(), slot.date()));
    }

    private static final LocalDate DAY = LocalDate.of(2026, 3, 2);

    // ---------- between ----------

    @Test
    @DisplayName("between: misma aula/fecha con franjas que se pisan → choque")
    void betweenSolapa() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 5L, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        List<Hit> hits = Clashes.between(List.of(a), ClashesTest::key, List.of(b), ClashesTest::key,
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).containsExactly(new Hit("a", "b"));
    }

    @Test
    @DisplayName("between: distinta aula → sin choque aunque la franja se pise")
    void betweenDistintaAulaNoChoca() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 6L, DAY, LocalTime.of(8, 30), LocalTime.of(9, 0));

        List<Hit> hits = Clashes.between(List.of(a), ClashesTest::key, List.of(b), ClashesTest::key,
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("between: distinta fecha → sin choque")
    void betweenDistintaFechaNoChoca() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 5L, DAY.plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 30));

        List<Hit> hits = Clashes.between(List.of(a), ClashesTest::key, List.of(b), ClashesTest::key,
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("between: fin del uno == inicio del otro no es solapamiento (medio-abierto)")
    void betweenBordeNoSolapa() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 5L, DAY, LocalTime.of(9, 30), LocalTime.of(10, 30));

        List<Hit> hits = Clashes.between(List.of(a), ClashesTest::key, List.of(b), ClashesTest::key,
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("between: extra en false descarta el par aunque la franja se pise")
    void betweenExtraFalsoDescarta() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 5L, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        List<Hit> hits = Clashes.between(List.of(a), ClashesTest::key, List.of(b), ClashesTest::key,
                (x, y) -> false, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("between: item con clave vacía (ej. classroomId null) nunca choca")
    void betweenSinClaveNoChoca() {
        Slot a = new Slot("a", null, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 5L, DAY, LocalTime.of(8, 30), LocalTime.of(9, 0));

        List<Hit> hits = Clashes.between(List.of(a), slot -> List.<RoomDate>of(),
                List.of(b), ClashesTest::key, (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }

    // ---------- within ----------

    @Test
    @DisplayName("within: cada par se emite una sola vez, no en ambos órdenes")
    void withinParUnaSolaVez() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("b", 5L, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Slot c = new Slot("c", 5L, DAY, LocalTime.of(11, 0), LocalTime.of(12, 0));

        List<Hit> hits = Clashes.within(List.of(a, b, c), ClashesTest::key,
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().a()).isIn("a", "b");
        assertThat(hits.getFirst().b()).isIn("a", "b");
    }

    @Test
    @DisplayName("within: extra permite excluir (ej. mismo evento nunca choca consigo mismo)")
    void withinExtraExcluye() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Slot b = new Slot("a", 5L, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        List<Hit> hits = Clashes.within(List.of(a, b), ClashesTest::key,
                (x, y) -> !x.id().equals(y.id()), (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("within: item que pertenece a varias claves (multi-fecha) choca una vez por fecha compartida")
    void withinMultiClaveChocaPorFechaCompartida() {
        record MultiDateSlot(String id, Long classroomId, List<LocalDate> dates, LocalTime startTime, LocalTime endTime)
                implements TimeSpan {
        }
        MultiDateSlot a = new MultiDateSlot("a", 5L, List.of(DAY, DAY.plusDays(7)), LocalTime.of(8, 0), LocalTime.of(9, 0));
        MultiDateSlot b = new MultiDateSlot("b", 5L, List.of(DAY, DAY.plusDays(7)), LocalTime.of(8, 30), LocalTime.of(9, 30));

        List<Hit> hits = Clashes.within(List.of(a, b),
                slot -> slot.dates().stream().map(d -> new RoomDate(slot.classroomId(), d)).toList(),
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).hasSize(2); // una por cada fecha compartida
    }

    @Test
    @DisplayName("within: sin items suficientes en el bucket no choca")
    void withinBucketUnicoNoChoca() {
        Slot a = new Slot("a", 5L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30));

        List<Hit> hits = Clashes.within(List.of(a), ClashesTest::key,
                (x, y) -> true, (x, y, k) -> new Hit(x.id(), y.id()));

        assertThat(hits).isEmpty();
    }
}
