package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface CommissionService {

    CommissionResponseDto findById(Long id);

    List<CommissionResponseDto> findByIds(Collection<Long> ids);

    List<CommissionResponseDto> findAll();

    CommissionResponseDto findByCourseAndPeriod(String courseCode, Integer periodYear, Integer periodSemester);
}
