package ar.edu.utn.frc.classroom_allocation.space.service;

import ar.edu.utn.frc.classroom_allocation.space.model.ClassroomType;

public interface ClassroomTypeService {

    ClassroomType findById(Integer id);

    ClassroomType findDefault();

}
