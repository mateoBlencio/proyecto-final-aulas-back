package ar.edu.utn.frc.siga.roomrequest.specification;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La whitelist es lo que separa un 400 propio de un 500 por {@code PropertyReferenceException}
 * cuando el cliente manda un {@code sort} que no existe (ver javadoc de {@link RoomRequestItemSort}).
 */
@DisplayName("RoomRequestItemSort")
class RoomRequestItemSortTest {

    @Test
    @DisplayName("campo permitido: se traduce y se le appendea id ASC como desempate")
    void allowedFieldIsTranslatedAndTiebreakerAppended() {
        Pageable requested = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "startTime"));

        Pageable result = RoomRequestItemSort.apply(requested);

        assertThat(result.getPageNumber()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(orders(result)).containsExactly(
                Sort.Order.desc("startTime"),
                Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("createdAt es un nombre público: la propiedad JPA real es request.createdAt")
    void createdAtTranslatesToRequestCreatedAt() {
        Pageable result = RoomRequestItemSort.apply(PageRequest.of(0, 20, Sort.by("createdAt")));

        assertThat(orders(result)).extracting(Sort.Order::getProperty).contains("request.createdAt");
        assertThat(orders(result)).extracting(Sort.Order::getProperty).doesNotContain("createdAt");
    }

    @Test
    @DisplayName("campo fuera de la whitelist: 400 propio, no un PropertyReferenceException sin traducir")
    void unknownFieldIsRejected() {
        Pageable requested = PageRequest.of(0, 20, Sort.by("teacherEmail"));

        assertThatThrownBy(() -> RoomRequestItemSort.apply(requested))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("teacherEmail");
    }

    @Test
    @DisplayName("si el cliente ya pidió id, no se duplica el desempate")
    void tiebreakerNotDuplicatedWhenAlreadyRequested() {
        Pageable result = RoomRequestItemSort.apply(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(orders(result)).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    @DisplayName("sin sort explícito: solo queda el desempate por id")
    void noSortStillGetsTiebreaker() {
        Pageable result = RoomRequestItemSort.apply(PageRequest.of(0, 20));

        assertThat(orders(result)).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("sort compuesto: cada campo se traduce en orden y el desempate va al final")
    void compositeSortTranslatesEachFieldInOrder() {
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("date"), Sort.Order.desc("createdAt")));

        Pageable result = RoomRequestItemSort.apply(requested);

        assertThat(orders(result)).containsExactly(
                Sort.Order.asc("date"),
                Sort.Order.desc("request.createdAt"),
                Sort.Order.asc("id"));
    }

    private static List<Sort.Order> orders(Pageable pageable) {
        return pageable.getSort().toList();
    }
}
