package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedConflictDto;
import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import org.springframework.data.domain.Sort;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;

final class UnallocatedConflictComparator {

    static final Set<String> SORTABLE = Set.of("subject", "dayOfWeek", "startTime", "enrolled", "commissionCode");

    private static final Comparator<UnallocatedConflictDto> BY_EVENT_ID =
            Comparator.comparing(c -> c.event().id());

    private UnallocatedConflictComparator() {
    }

    static void validate(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty(), SORTABLE);
            }
        }
    }

    static Comparator<UnallocatedConflictDto> of(Sort sort) {
        Comparator<UnallocatedConflictDto> result = null;
        for (Sort.Order order : sort) {
            Comparator<UnallocatedConflictDto> next = byProperty(order.getProperty(), order.isAscending());
            result = result == null ? next : result.thenComparing(next);
        }
        return result == null ? BY_EVENT_ID : result.thenComparing(BY_EVENT_ID);
    }

    static DayOfWeek dayOf(AcademicEventResponseDto event) {
        return switch (event) {
            case RecurringEventResponseDto r -> r.dayOfWeek();
            case UniqueEventResponseDto u -> u.date().getDayOfWeek();
        };
    }

    private static Comparator<UnallocatedConflictDto> byProperty(String property, boolean asc) {
        return switch (property) {
            case "subject" -> direct(c -> c.event().subject() == null ? "" : c.event().subject().name().toLowerCase(), asc);
            case "dayOfWeek" -> {
                Comparator<UnallocatedConflictDto> day = direct(c -> dayOf(c.event()), asc);
                Comparator<UnallocatedConflictDto> date = direct(c -> uniqueDate(c.event()), asc);
                yield day.thenComparing(date);
            }
            case "startTime" -> direct(c -> c.event().startTime(), asc);
            case "enrolled" -> direct(c -> c.event().enrolled() == null ? 0 : c.event().enrolled(), asc);
            case "commissionCode" -> {
                Comparator<String> nullsLast = Comparator.nullsLast(asc
                        ? Comparator.<String>naturalOrder() : Comparator.<String>reverseOrder());
                yield Comparator.comparing(c -> commissionCode(c.event()), nullsLast);
            }
            default -> throw new InvalidSortPropertyException(property, SORTABLE);
        };
    }

    private static <K extends Comparable<? super K>> Comparator<UnallocatedConflictDto> direct(
            Function<UnallocatedConflictDto, K> key, boolean asc) {
        Comparator<UnallocatedConflictDto> cmp = Comparator.comparing(key);
        return asc ? cmp : cmp.reversed();
    }

    // Los recurrentes van antes que los únicos del mismo día (null primero)
    private static LocalDate uniqueDate(AcademicEventResponseDto event) {
        return event instanceof UniqueEventResponseDto u ? u.date() : LocalDate.MIN;
    }

    private static String commissionCode(AcademicEventResponseDto event) {
        var commission = switch (event) {
            case RecurringEventResponseDto r -> r.commission();
            case UniqueEventResponseDto u -> u.commission();
        };
        return commission == null ? null : commission.courseCode();
    }
}
