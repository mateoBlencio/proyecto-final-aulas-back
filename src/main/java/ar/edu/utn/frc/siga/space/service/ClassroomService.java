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

    ClassroomResponseDto findById(Integer id);

    List<ClassroomResponseDto> findAllAvailable();

    List<ClassroomResponseDto> findByIds(Collection<Integer> ids);

    Page<ClassroomResponseDto> findAll(ClassroomFilter filter, Pageable pageable);

    ClassroomResponseDto update(Integer id, ClassroomRequestDto dto);

    void delete(Integer id);

    ClassroomResponseDto findByRoomNumberAndBuilding(String roomNumber, Integer buildingId);
}
