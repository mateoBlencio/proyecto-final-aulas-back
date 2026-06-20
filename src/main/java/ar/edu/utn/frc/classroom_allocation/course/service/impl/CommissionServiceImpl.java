package ar.edu.utn.frc.classroom_allocation.course.service.impl;

import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.repository.CommissionRepository;
import ar.edu.utn.frc.classroom_allocation.course.service.CommissionService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRepository commissionRepository;

    @Override
    public Optional<Commission> findByCourseCodeAndCommissionNumberAndPeriodAndDeletedFalse(
            String courseCode, Integer commissionNumber, AcademicPeriod period) {
        return commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriodAndDeletedFalse(
                courseCode, commissionNumber, period);
    }

    @Override
    @Transactional
    public Commission save(Commission commission) {
        return commissionRepository.save(commission);
    }
}
