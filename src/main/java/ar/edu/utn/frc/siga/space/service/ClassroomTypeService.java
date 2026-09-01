package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClassroomTypeService extends ActivationService<Long> {

    ClassroomType findById(Long id);

    Page<ClassroomTypeResponseDto> findAll(boolean includeDeactivated, Pageable pageable);

    ClassroomTypeResponseDto findDtoById(Long id);

    ClassroomTypeResponseDto create(ClassroomTypeRequestDto dto);

    ClassroomTypeResponseDto update(Long id, ClassroomTypeRequestDto dto);

}
