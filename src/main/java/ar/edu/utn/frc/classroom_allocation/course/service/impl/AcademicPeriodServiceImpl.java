package ar.edu.utn.frc.classroom_allocation.course.service.impl;

import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.classroom_allocation.course.service.AcademicPeriodService;
import java.util.Optional;
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
    public Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester) {
        log.debug("Finding AcademicPeriod: year={}, semester={}", year, semester);
        return academicPeriodRepository.findByYearAndSemester(year, semester);
    }

    @Override
    @Transactional
    public AcademicPeriod save(AcademicPeriod academicPeriod) {
        log.debug("Saving AcademicPeriod: year={}, semester={}",
                academicPeriod.getYear(), academicPeriod.getSemester());
        AcademicPeriod saved = academicPeriodRepository.save(academicPeriod);
        log.info("AcademicPeriod saved: id={}", saved.getId());
        return saved;
    }
}
