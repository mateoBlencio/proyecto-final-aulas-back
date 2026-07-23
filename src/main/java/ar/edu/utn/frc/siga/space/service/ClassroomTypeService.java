package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.model.ClassroomType;

/**
 * Consulta de tipos de aula.
 */
public interface ClassroomTypeService {

    ClassroomType findById(Integer id);

}
