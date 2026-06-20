package ar.edu.utn.frc.classroom_allocation.space.service;

import ar.edu.utn.frc.classroom_allocation.space.model.Building;

public interface BuildingService {

    Building findById(Integer id);

    Building findByName(String name);
}
