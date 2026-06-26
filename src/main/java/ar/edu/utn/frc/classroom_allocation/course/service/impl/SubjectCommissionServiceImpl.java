package ar.edu.utn.frc.classroom_allocation.course.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.course.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.classroom_allocation.course.service.SubjectCommissionService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectCommissionServiceImpl implements SubjectCommissionService {

    private final SubjectCommissionRepository subjectCommissionRepository;

    @Override
    public Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedFalse(Subject subject, Commission commission) {
        log.debug("Finding SubjectCommission: subjectId={}, commissionId={}",
                subject.getId(), commission.getId());
        return subjectCommissionRepository.findBySubjectAndCommissionAndDeletedFalse(subject, commission);
    }

    @Override
    @Transactional
    public SubjectCommission save(SubjectCommission subjectCommission) {
        log.debug("Saving SubjectCommission: subjectId={}, commissionId={}",
                subjectCommission.getSubject().getId(), subjectCommission.getCommission().getId());
        SubjectCommission saved = subjectCommissionRepository.save(subjectCommission);
        log.info("SubjectCommission saved: id={}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public FindOrCreateResult<SubjectCommission> findOrCreate(Subject subject, Commission commission,
            Integer enrolledCount) {
        return subjectCommissionRepository.findBySubjectAndCommissionAndDeletedFalse(subject, commission)
            .map(found -> new FindOrCreateResult<>(found, false))
            .orElseGet(() -> {
                log.info("Creando SubjectCommission: subject={}, commission={}",
                    subject.getId(), commission.getId());
                SubjectCommission created = subjectCommissionRepository.save(
                    SubjectCommission.builder()
                        .subject(subject)
                        .commission(commission)
                        .enrolledCount(enrolledCount)
                        .build()
                );
                return new FindOrCreateResult<>(created, true);
            });
    }
}
