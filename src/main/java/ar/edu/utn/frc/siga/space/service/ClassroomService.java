package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface("api")
public interface ClassroomService {

    ClassroomResponseDto create(ClassroomRequestDto dto);

    ClassroomResponseDto findById(Integer id);

    /** Todas las aulas disponibles (no eliminadas) para la asignación automática. */
    List<ClassroomResponseDto> findAllAvailable();

    Classroom requireById(Integer id);

    Page<ClassroomResponseDto> findAll(ClassroomFilter filter, Pageable pageable);

    ClassroomResponseDto update(Integer id, ClassroomRequestDto dto);

    void delete(Integer id);

    FindOrCreateResult<Classroom> findOrCreate(String roomNumber, Building building, Integer enrolledCount);
}
