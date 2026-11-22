package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.academic.dto.AcademicPeriodFilter;
import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.event.AcademicPeriodChanged;
import ar.edu.utn.frc.siga.academic.mapper.AcademicPeriodMapper;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.specification.AcademicPeriodSpecification;
import ar.edu.utn.frc.siga.academic.validator.AcademicPeriodUpdateValidator;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    @AuditOperation(value = "Alta de período académico", subject = "AcademicPeriod")
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
    public List<AcademicPeriodResponseDto> findRecessesBetween(LocalDate from, LocalDate to) {
        return academicPeriodRepository
                .findBySemesterAndDeletedAtIsNullAndRecessStartLessThanEqualAndRecessEndGreaterThanEqual(
                        TermType.ANUAL.getSemester(), to, from).stream()
                .map(academicPeriodMapper::toDto)
                .toList();
    }

    @Override
    public Optional<AcademicPeriodResponseDto> findAnnual(int year) {
        return academicPeriodRepository.findByYearAndSemester(year, TermType.ANUAL.getSemester())
                .filter(period -> !period.isDeleted())
                .map(academicPeriodMapper::toDto);
    }

    @Override
    @Transactional
    @AuditOperation(value = "Modificación de período académico", subject = "AcademicPeriod")
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

    @Override
    public Page<AcademicPeriodResponseDto> findAll(AcademicPeriodFilter filter, Pageable pageable,
            boolean includeDeactivated) {
        return academicPeriodRepository.findAll(
                        AcademicPeriodSpecification.withFilter(filter)
                                .and(SoftDeleteSpecifications.activeUnless(includeDeactivated)),
                        pageable)
                .map(academicPeriodMapper::toDto);
    }

    @Override
    public AcademicPeriodResponseDto findById(Long id) {
        return academicPeriodMapper.toDto(academicPeriodRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicPeriod", id)));
    }

    @Override
    @Transactional
    @AuditOperation(value = "Reactivación de período académico", subject = "AcademicPeriod")
    public void activate(Long id) {
        academicPeriodRepository.restore(Finder.orThrow(academicPeriodRepository::findById, id, "AcademicPeriod"));
    }

    @Override
    @Transactional
    @AuditOperation(value = "Baja de período académico", subject = "AcademicPeriod")
    public void deactivate(Long id) {
        academicPeriodRepository.softDelete(Finder.orThrow(academicPeriodRepository::findById, id, "AcademicPeriod"));
    }
}
