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
        return of(sort, UnallocatedConflictDto::event);
    }

    static <T> Comparator<T> of(Sort sort, Function<T, AcademicEventResponseDto> eventOf) {
        Comparator<T> byEventId = Comparator.comparing(c -> eventOf.apply(c).id());
        Comparator<T> result = null;
        for (Sort.Order order : sort) {
            Comparator<T> next = byProperty(order.getProperty(), order.isAscending(), eventOf);
            result = result == null ? next : result.thenComparing(next);
        }
        return result == null ? byEventId : result.thenComparing(byEventId);
    }

    static DayOfWeek dayOf(AcademicEventResponseDto event) {
        return switch (event) {
            case RecurringEventResponseDto r -> r.dayOfWeek();
            case UniqueEventResponseDto u -> u.date().getDayOfWeek();
        };
    }

    private static <T> Comparator<T> byProperty(String property, boolean asc,
                                                Function<T, AcademicEventResponseDto> eventOf) {
        return switch (property) {
            case "subject" -> direct(c -> eventOf.apply(c).subject() == null
                    ? "" : eventOf.apply(c).subject().name().toLowerCase(), asc);
            case "dayOfWeek" -> {
                Comparator<T> day = direct(c -> dayOf(eventOf.apply(c)), asc);
                Comparator<T> date = direct(c -> uniqueDate(eventOf.apply(c)), asc);
                yield day.thenComparing(date);
            }
            case "startTime" -> direct(c -> eventOf.apply(c).startTime(), asc);
            case "enrolled" -> direct(c -> eventOf.apply(c).enrolled() == null ? 0 : eventOf.apply(c).enrolled(), asc);
            case "commissionCode" -> {
                Comparator<String> nullsLast = Comparator.nullsLast(asc
                        ? Comparator.<String>naturalOrder() : Comparator.<String>reverseOrder());
                yield Comparator.comparing(c -> commissionCode(eventOf.apply(c)), nullsLast);
            }
            default -> throw new InvalidSortPropertyException(property, SORTABLE);
        };
    }

    private static <T, K extends Comparable<? super K>> Comparator<T> direct(Function<T, K> key, boolean asc) {
        Comparator<T> cmp = Comparator.comparing(key);
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
