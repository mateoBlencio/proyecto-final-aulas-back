package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDTO;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface ClassroomService {

    ClassroomResponseDTO create(ClassroomRequestDTO dto);

    ClassroomResponseDTO findById(Integer id);

    Classroom requireById(Integer id);

    Classroom findByRoomNumberAndBuilding(String roomNumber, Building building);

    Page<ClassroomResponseDTO> findAll(ClassroomFilter filter, Pageable pageable);

    ClassroomResponseDTO update(Integer id, ClassroomRequestDTO dto);

    void delete(Integer id);

    FindOrCreateResult<Classroom> findOrCreate(String roomNumber, Building building, Integer enrolledCount);
}
