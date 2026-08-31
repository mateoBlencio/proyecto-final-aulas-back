package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.AcademicPeriodMapper;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AcademicPeriodServiceImpl implements AcademicPeriodService {

    private final AcademicPeriodRepository academicPeriodRepository;
    private final AcademicPeriodMapper academicPeriodMapper;

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
