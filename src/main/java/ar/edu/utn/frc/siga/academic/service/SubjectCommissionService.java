package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectCommissionService {

    FindOrCreateResult<SubjectCommissionResponseDto> findOrCreate(Long subjectId, Long commissionId, Integer enrolledCount);
}
