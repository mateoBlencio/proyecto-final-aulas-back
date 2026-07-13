package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.model.ClassroomType;

/**
 * Consulta de tipos de aula, incluyendo la resolución del tipo por defecto usado
 * cuando un aula se crea sin tipo explícito (p. ej. find-or-create).
 */
public interface ClassroomTypeService {

    ClassroomType findById(Integer id);

    /**
     * Tipo de aula por defecto (configurable vía {@code siga.space.default-classroom-type},
     * "Normal" si no se configura); lanza {@link ar.edu.utn.frc.siga.space.exception.SpaceDomainException}
     * si ese tipo no existe en la base.
     */
    ClassroomType findDefault();

}
