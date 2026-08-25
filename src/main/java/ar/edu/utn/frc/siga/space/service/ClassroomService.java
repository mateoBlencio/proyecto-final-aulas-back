package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.util.Collection;
import java.util.List;

@NamedInterface("api")
public interface ClassroomService {

    ClassroomResponseDto create(ClassroomRequestDto dto);

    ClassroomResponseDto findById(Long id);

    List<ClassroomResponseDto> findAllAvailable();

    List<ClassroomResponseDto> findByIds(Collection<Long> ids);

    Page<ClassroomResponseDto> findAll(ClassroomFilter filter, Pageable pageable);

    ClassroomResponseDto update(Long id, ClassroomRequestDto dto);

    void delete(Long id);

    ClassroomResponseDto findByRoomNumberAndBuilding(Integer roomNumber, Long buildingId);
}
