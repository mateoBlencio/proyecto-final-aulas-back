package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.space.dto.request.ResourceTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ResourceTypeResponseDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ResourceTypeMapper;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import ar.edu.utn.frc.siga.space.service.ResourceTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ResourceTypeServiceImpl implements ResourceTypeService {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceTypeMapper resourceTypeMapper;

    @Override
    public Page<ResourceTypeResponseDto> findAll(boolean includeDeactivated, Pageable pageable) {
        log.debug("Listando tipos de recurso: includeDeactivated={}, page={}, size={}",
                includeDeactivated, pageable.getPageNumber(), pageable.getPageSize());
        return (includeDeactivated
                ? resourceTypeRepository.findAll(pageable)
                : resourceTypeRepository.findAllActive(pageable))
                .map(resourceTypeMapper::toDto);
    }

    @Override
    public ResourceTypeResponseDto findById(Long id) {
        log.debug("Buscando tipo de recurso: id={}", id);
        return resourceTypeMapper.toDto(findExistingById(id));
    }

    @Override
    @Transactional
    public ResourceTypeResponseDto create(ResourceTypeRequestDto dto) {
        log.debug("Creando tipo de recurso: name={}, valueKind={}", dto.name(), dto.valueKind());
        rejectDuplicateName(dto.name(), null);
        ResourceType saved = resourceTypeRepository.save(ResourceType.builder()
                .name(dto.name())
                .valueKind(dto.valueKind())
                .build());
        log.info("Tipo de recurso creado: id={}", saved.getId());
        return resourceTypeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ResourceTypeResponseDto update(Long id, ResourceTypeRequestDto dto) {
        log.debug("Actualizando tipo de recurso: id={}", id);
        ResourceType resourceType = findExistingById(id);
        rejectDuplicateName(dto.name(), id);
        resourceType.setName(dto.name());
        resourceType.setValueKind(dto.valueKind());
        ResourceType saved = resourceTypeRepository.save(resourceType);
        log.info("Tipo de recurso actualizado: id={}", id);
        return resourceTypeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        resourceTypeRepository.restore(Finder.orThrow(resourceTypeRepository::findById, id, "ResourceType"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        resourceTypeRepository.softDelete(Finder.orThrow(resourceTypeRepository::findById, id, "ResourceType"));
    }

    private void rejectDuplicateName(String name, Long selfId) {
        boolean duplicate = selfId == null
                ? resourceTypeRepository.existsByNameIgnoreCase(name)
                : resourceTypeRepository.existsByNameIgnoreCaseAndIdNot(name, selfId);
        if (duplicate) {
            throw new SpaceDomainException("Ya existe un tipo de recurso con el nombre: " + name);
        }
    }

    private ResourceType findExistingById(Long id) {
        return resourceTypeRepository.findActiveById(id)
                .orElseThrow(() -> {
                    log.warn("Tipo de recurso no encontrado: id={}", id);
                    return ResourceNotFoundException.of("ResourceType", id);
                });
    }
}
