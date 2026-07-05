package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
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
