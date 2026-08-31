package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.space.model.ClassroomType;

public interface ClassroomTypeService extends ActivationService<Long> {

    ClassroomType findById(Long id);

}
