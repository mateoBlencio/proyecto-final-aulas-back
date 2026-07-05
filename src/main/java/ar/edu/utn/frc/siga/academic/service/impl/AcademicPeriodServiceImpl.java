package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AcademicPeriodServiceImpl implements AcademicPeriodService {

    private final AcademicPeriodRepository academicPeriodRepository;

    @Override
    @Transactional
    public AcademicPeriod save(AcademicPeriod academicPeriod) {
        log.debug("Saving AcademicPeriod: year={}, semester={}",
                academicPeriod.getYear(), academicPeriod.getSemester());
        AcademicPeriod saved = academicPeriodRepository.save(academicPeriod);
        log.info("AcademicPeriod saved: id={}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public FindOrCreateResult<AcademicPeriod> findOrCreate(Integer year, TermType termType) {
        return academicPeriodRepository.findByYearAndSemester(year, termType.getSemester())
            .map(found -> new FindOrCreateResult<>(found, false))
            .orElseGet(() -> {
                log.info("Creando AcademicPeriod: year={}, semester={}", year, termType.getSemester());
                AcademicPeriod created = academicPeriodRepository.save(
                    AcademicPeriod.builder()
                        .year(year)
                        .semester(termType.getSemester())
                        .startDate(termType.startDate(year))
                        .endDate(termType.endDate(year))
                        .build()
                );
                return new FindOrCreateResult<>(created, true);
            });
    }
}
