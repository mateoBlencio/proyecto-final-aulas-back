package ar.edu.utn.frc.classroom_allocation.course.service.impl;

import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.classroom_allocation.course.service.AcademicPeriodService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AcademicPeriodServiceImpl implements AcademicPeriodService {

    private final AcademicPeriodRepository academicPeriodRepository;

    @Override
    public Optional<AcademicPeriod> findByYearAndSemester(Integer year, Integer semester) {
        return academicPeriodRepository.findByYearAndSemester(year, semester);
    }

    @Override
    @Transactional
    public AcademicPeriod save(AcademicPeriod academicPeriod) {
        return academicPeriodRepository.save(academicPeriod);
    }
}
