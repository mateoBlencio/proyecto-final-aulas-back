package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomTypeMapper;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
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
public class ClassroomTypeServiceImpl implements ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;
    private final ClassroomTypeMapper classroomTypeMapper;

    @Override
    public ClassroomType findById(Long id) {
        log.debug("Buscando tipo de aula: id={}", id);
        return findExistingById(id);
    }

    @Override
    public Page<ClassroomTypeResponseDto> findAll(boolean includeDeactivated, Pageable pageable) {
        log.debug("Listando tipos de aula: includeDeactivated={}, page={}, size={}",
                includeDeactivated, pageable.getPageNumber(), pageable.getPageSize());
        return (includeDeactivated
                ? classroomTypeRepository.findAll(pageable)
                : classroomTypeRepository.findAllActive(pageable))
                .map(classroomTypeMapper::toDto);
    }

    @Override
    public ClassroomTypeResponseDto findDtoById(Long id) {
        log.debug("Buscando tipo de aula (dto): id={}", id);
        return classroomTypeMapper.toDto(findExistingById(id));
    }

    @Override
    @Transactional
    public ClassroomTypeResponseDto create(ClassroomTypeRequestDto dto) {
        log.debug("Creando tipo de aula: description={}", dto.description());
        rejectDuplicateDescription(dto.description(), null);
        ClassroomType saved = classroomTypeRepository.save(ClassroomType.builder()
                .description(dto.description())
                .build());
        log.info("Tipo de aula creado: id={}", saved.getId());
        return classroomTypeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ClassroomTypeResponseDto update(Long id, ClassroomTypeRequestDto dto) {
        log.debug("Actualizando tipo de aula: id={}", id);
        ClassroomType classroomType = findExistingById(id);
        rejectDuplicateDescription(dto.description(), id);
        classroomType.setDescription(dto.description());
        ClassroomType saved = classroomTypeRepository.save(classroomType);
        log.info("Tipo de aula actualizado: id={}", id);
        return classroomTypeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        classroomTypeRepository.restore(Finder.orThrow(classroomTypeRepository::findById, id, "ClassroomType"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        classroomTypeRepository.softDelete(Finder.orThrow(classroomTypeRepository::findById, id, "ClassroomType"));
    }

    private void rejectDuplicateDescription(String description, Long selfId) {
        boolean duplicate = selfId == null
                ? classroomTypeRepository.existsByDescriptionIgnoreCase(description)
                : classroomTypeRepository.existsByDescriptionIgnoreCaseAndIdNot(description, selfId);
        if (duplicate) {
            throw new SpaceDomainException("Ya existe un tipo de aula con la descripción: " + description);
        }
    }

    private ClassroomType findExistingById(Long id) {
        return classroomTypeRepository.findActiveById(id)
                .orElseThrow(() -> {
                    log.warn("Tipo de aula no encontrado: id={}", id);
                    return ResourceNotFoundException.of("ClassroomType", id);
                });
    }

}
