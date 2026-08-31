package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.space.dto.request.ResourceTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ResourceTypeResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResourceTypeService extends ActivationService<Long> {

    Page<ResourceTypeResponseDto> findAll(boolean includeDeactivated, Pageable pageable);

    ResourceTypeResponseDto findById(Long id);

    ResourceTypeResponseDto create(ResourceTypeRequestDto dto);

    ResourceTypeResponseDto update(Long id, ResourceTypeRequestDto dto);
}
