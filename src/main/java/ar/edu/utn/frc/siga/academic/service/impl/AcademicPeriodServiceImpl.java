package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.event.AcademicPeriodChanged;
import ar.edu.utn.frc.siga.academic.mapper.AcademicPeriodMapper;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.util.WeekAlignedDates;
import ar.edu.utn.frc.siga.academic.validator.AcademicPeriodUpdateValidator;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AcademicPeriodServiceImpl implements AcademicPeriodService {

    private final AcademicPeriodRepository academicPeriodRepository;
    private final AcademicPeriodMapper academicPeriodMapper;
    private final AcademicPeriodUpdateValidator updateValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public FindOrCreateResult<AcademicPeriodResponseDto> findOrCreate(Integer year, TermType termType) {
        return FindOrCreateResult.resolve(
                academicPeriodRepository.findByYearAndSemester(year, termType.getSemester()),
                () -> {
                    log.info("Creando AcademicPeriod: year={}, semester={}", year, termType.getSemester());
                    return academicPeriodRepository.save(
                            AcademicPeriod.builder()
                                    .year(year)
                                    .semester(termType.getSemester())
                                    .startDate(termType.startDate(year))
                                    .endDate(termType.endDate(year))
                                    .build());
                }
        ).map(academicPeriodMapper::toDto);
    }

    @Override
    public List<AcademicPeriodResponseDto> findActive() {
        log.debug("Buscando períodos académicos activos");
        return academicPeriodRepository.findAllActive().stream()
                .map(academicPeriodMapper::toDto)
                .toList();
    }

    @Override
    public List<AcademicPeriodResponseDto> findCurrent() {
        LocalDate today = LocalDate.now();
        return academicPeriodRepository.findAllActive().stream()
                .filter(period -> isCurrent(period, today))
                .map(academicPeriodMapper::toDto)
                .toList();
    }

    /**
     * Comando explícito de materialización: si el año en curso no tiene filas, las genera desde el
     * año previo (rollover perezoso). Separado de {@link #findCurrent()} para que una lectura no
     * escriba. Tolera la carrera contra {@code @UniqueConstraint(anio, cuatrimestre)}: si otra
     * transacción concurrente ya insertó, la violación de integridad se ignora.
     */
    @Override
    @Transactional
    public void materializeCurrentYear() {
        int year = LocalDate.now().getYear();
        if (!academicPeriodRepository.findByYearAndDeletedAtIsNull(year).isEmpty()) {
            return;
        }
        try {
            rolloverFrom(year - 1, year);
        } catch (DataIntegrityViolationException e) {
            log.info("Rollover de {} ya materializado por otra transacción concurrente", year);
        }
    }

    @Override
    @Transactional
    public AcademicPeriodResponseDto update(Long id, UpdateAcademicPeriodRequestDto dto) {
        AcademicPeriod period = academicPeriodRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicPeriod", id));
        updateValidator.validate(period, dto);

        if (dto.startDate() != null) {
            period.setStartDate(dto.startDate());
        }
        period.setEndDate(dto.endDate());
        period.setRecessStart(dto.recessStart());
        period.setRecessEnd(dto.recessEnd());
        academicPeriodRepository.save(period);
        publishChanged(period);

        if (period.getSemester() == TermType.ANUAL.getSemester()
                && dto.recessStart() != null && dto.recessEnd() != null) {
            syncTermsToRecess(period.getYear(), dto.recessStart(), dto.recessEnd());
        }

        log.info("Período académico modificado: id={}, fin={}, receso={}..{}",
                id, dto.endDate(), dto.recessStart(), dto.recessEnd());
        return academicPeriodMapper.toDto(period);
    }

    private void syncTermsToRecess(Integer year, LocalDate recessStart, LocalDate recessEnd) {
        academicPeriodRepository.findByYearAndSemester(year, TermType.PRIMER_CUATRIMESTRE.getSemester())
                .ifPresent(firstTerm -> {
                    firstTerm.setEndDate(recessStart.minusDays(1));
                    academicPeriodRepository.save(firstTerm);
                    publishChanged(firstTerm);
                });
        academicPeriodRepository.findByYearAndSemester(year, TermType.SEGUNDO_CUATRIMESTRE.getSemester())
                .ifPresent(secondTerm -> {
                    secondTerm.setStartDate(recessEnd.plusDays(1));
                    academicPeriodRepository.save(secondTerm);
                    publishChanged(secondTerm);
                });
    }

    private void publishChanged(AcademicPeriod period) {
        eventPublisher.publishEvent(new AcademicPeriodChanged(
                period.getId(), period.getYear(), period.getSemester(),
                period.getStartDate(), period.getEndDate(),
                period.getRecessStart(), period.getRecessEnd()));
    }

    private void rolloverFrom(int sourceYear, int targetYear) {
        for (AcademicPeriod source : academicPeriodRepository.findByYearAndDeletedAtIsNull(sourceYear)) {
            FindOrCreateResult.resolve(
                    academicPeriodRepository.findByYearAndSemester(targetYear, source.getSemester()),
                    () -> {
                        log.info("Rollover perezoso de AcademicPeriod: year={}, semester={}",
                                targetYear, source.getSemester());
                        return academicPeriodRepository.save(AcademicPeriod.builder()
                                .year(targetYear)
                                .semester(source.getSemester())
                                .startDate(alignedOrNull(source.getStartDate(), targetYear))
                                .endDate(alignedOrNull(source.getEndDate(), targetYear))
                                .recessStart(alignedOrNull(source.getRecessStart(), targetYear))
                                .recessEnd(alignedOrNull(source.getRecessEnd(), targetYear))
                                .build());
                    });
        }
    }

    private static LocalDate alignedOrNull(LocalDate date, int targetYear) {
        return date != null ? WeekAlignedDates.sameWeekOfMonth(date, targetYear) : null;
    }

    private static boolean isCurrent(AcademicPeriod period, LocalDate today) {
        return period.getStartDate() != null && period.getEndDate() != null
                && !today.isBefore(period.getStartDate()) && !today.isAfter(period.getEndDate());
    }

    @Override
    public List<AcademicPeriodResponseDto> findAll(boolean includeDeactivated) {
        List<AcademicPeriod> periods = includeDeactivated
                ? academicPeriodRepository.findAll()
                : academicPeriodRepository.findAllActive();
        return periods.stream()
                .map(academicPeriodMapper::toDto)
                .toList();
    }

    @Override
    public AcademicPeriodResponseDto findById(Long id) {
        return academicPeriodMapper.toDto(academicPeriodRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicPeriod", id)));
    }

    @Override
    @Transactional
    public void activate(Long id) {
        academicPeriodRepository.restore(Finder.orThrow(academicPeriodRepository::findById, id, "AcademicPeriod"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        academicPeriodRepository.softDelete(Finder.orThrow(academicPeriodRepository::findById, id, "AcademicPeriod"));
    }
}
