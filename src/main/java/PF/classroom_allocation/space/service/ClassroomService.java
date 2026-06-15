package PF.classroom_allocation.space.service;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.dto.request.ClassroomRequestDTO;
import PF.classroom_allocation.space.dto.response.ClassroomResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClassroomService {

    ClassroomResponseDTO create(ClassroomRequestDTO dto);

    ClassroomResponseDTO findById(Integer id);

    Page<ClassroomResponseDTO> findAll(ClassroomFilter filter, Pageable pageable);

    ClassroomResponseDTO update(Integer id, ClassroomRequestDTO dto);

    void delete(Integer id);

}
