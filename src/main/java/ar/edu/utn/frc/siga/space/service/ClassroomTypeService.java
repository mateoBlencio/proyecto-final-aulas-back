package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import java.util.List;

public interface ClassroomTypeService extends ActivationService<Long> {

    ClassroomType findById(Long id);

    List<ClassroomTypeResponseDto> findAll(boolean includeDeactivated);

}
