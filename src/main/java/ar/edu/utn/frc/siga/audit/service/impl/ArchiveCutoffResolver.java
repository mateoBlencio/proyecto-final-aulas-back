package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cutoff of the audit archive: the start of the cycle before the current one. A cycle starts with the
 * earliest {@code startDate} among the active periods of its year (normally March 1st, but what the
 * Subsecretaría loaded rules). Without the start of the current year or of the previous cycle there is no
 * cutoff and nothing is purged.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ArchiveCutoffResolver {

    /** {@code retainedFromCycle} is the oldest cycle kept online; everything before {@code startDate} is archived. */
    record ArchiveCutoff(int retainedFromCycle, LocalDate startDate) {

        LocalDateTime instant() {
            return startDate.atStartOfDay();
        }
    }

    /** Either a cutoff or the outcome that explains why there is none ({@code NO_PERIODS}, {@code INVALID_PERIODS}). */
    record Resolution(ArchiveCutoff cutoff, AuditArchiveOutcome failure) {

        static Resolution of(ArchiveCutoff cutoff) {
            return new Resolution(cutoff, null);
        }

        static Resolution failed(AuditArchiveOutcome outcome) {
            return new Resolution(null, outcome);
        }
    }

    /**
     * Safety net: the cutoff must be strictly before the start of the current cycle and at least 12 months
     * old, so a period loaded with a wrong date (e.g. before March) never makes the archive eat recent history.
     */
    static Resolution resolve(List<AcademicPeriodResponseDto> periods, LocalDate today) {
        int year = today.getYear();
        Optional<LocalDate> startOfYear = cycleStart(periods, year);
        if (startOfYear.isEmpty()) {
            log.warn("Archivado de auditoría omitido: no hay fecha de inicio del ciclo {}", year);
            return Resolution.failed(AuditArchiveOutcome.NO_PERIODS);
        }
        // Between January and the start of the cycle, the current one is still the previous year's.
        int current = startOfYear.get().isAfter(today) ? year - 1 : year;
        int retained = current - 1;
        Optional<LocalDate> currentStart = cycleStart(periods, current);
        Optional<LocalDate> cutoff = cycleStart(periods, retained);
        if (currentStart.isEmpty() || cutoff.isEmpty()) {
            log.warn("Archivado de auditoría omitido: faltan fechas de inicio de los ciclos {} o {}", current, retained);
            return Resolution.failed(AuditArchiveOutcome.NO_PERIODS);
        }
        LocalDate oldestAllowed = today.minusMonths(12);
        if (!cutoff.get().isBefore(currentStart.get()) || cutoff.get().isAfter(oldestAllowed)) {
            log.warn("Archivado de auditoría omitido: corte {} inválido (inicio del ciclo {}: {}, hoy: {}, máximo permitido: {})",
                    cutoff.get(), current, currentStart.get(), today, oldestAllowed);
            return Resolution.failed(AuditArchiveOutcome.INVALID_PERIODS);
        }
        return Resolution.of(new ArchiveCutoff(retained, cutoff.get()));
    }

    private static Optional<LocalDate> cycleStart(List<AcademicPeriodResponseDto> periods, int year) {
        return periods.stream()
                .filter(period -> period.year() != null && period.year() == year)
                .map(AcademicPeriodResponseDto::startDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo);
    }
}
