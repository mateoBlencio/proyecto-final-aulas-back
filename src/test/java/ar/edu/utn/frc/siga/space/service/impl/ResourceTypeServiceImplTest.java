package ar.edu.utn.frc.siga.space.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.dto.request.ResourceTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ResourceTypeResponseDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ResourceTypeMapper;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.space.repository.ResourceTypeRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceTypeServiceImpl")
class ResourceTypeServiceImplTest {

    @Mock
    private ResourceTypeRepository resourceTypeRepository;
    @Mock
    private ResourceTypeMapper resourceTypeMapper;

    private ResourceTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceTypeServiceImpl(resourceTypeRepository, resourceTypeMapper);
        lenient().when(resourceTypeMapper.toDto(any()))
                .thenReturn(new ResourceTypeResponseDto(1L, "Pizarrón digital", ResourceValueKind.BOOLEAN, true));
    }

    private ResourceType type() {
        return ResourceType.builder().id(1L).name("Proyector").valueKind(ResourceValueKind.BOOLEAN).build();
    }

    @Test
    @DisplayName("findById: 404 si el tipo de recurso no existe")
    void findByIdMissingThrows() {
        when(resourceTypeRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ResourceType");
    }

    @Test
    @DisplayName("create: persiste cuando el nombre no está duplicado")
    void createPersists() {
        when(resourceTypeRepository.existsByNameIgnoreCase("Pizarrón digital")).thenReturn(false);
        when(resourceTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new ResourceTypeRequestDto("Pizarrón digital", ResourceValueKind.BOOLEAN));

        verify(resourceTypeRepository).save(any(ResourceType.class));
    }

    @Test
    @DisplayName("create: nombre duplicado (incluye desactivados) lanza SpaceDomainException")
    void createDuplicateNameThrows() {
        when(resourceTypeRepository.existsByNameIgnoreCase("Proyector")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ResourceTypeRequestDto("Proyector", ResourceValueKind.BOOLEAN)))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("Proyector");
        verify(resourceTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: cambia nombre y valueKind cuando no colisiona")
    void updateChangesFields() {
        ResourceType type = type();
        when(resourceTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));
        when(resourceTypeRepository.existsByNameIgnoreCaseAndIdNot("Aire", 1L)).thenReturn(false);
        when(resourceTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, new ResourceTypeRequestDto("Aire", ResourceValueKind.COUNT));

        assertThat(type.getName()).isEqualTo("Aire");
        assertThat(type.getValueKind()).isEqualTo(ResourceValueKind.COUNT);
    }

    @Test
    @DisplayName("update: nombre usado por otro tipo lanza SpaceDomainException")
    void updateDuplicateNameThrows() {
        ResourceType type = type();
        when(resourceTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));
        when(resourceTypeRepository.existsByNameIgnoreCaseAndIdNot("Aire", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new ResourceTypeRequestDto("Aire", ResourceValueKind.COUNT)))
                .isInstanceOf(SpaceDomainException.class);
        verify(resourceTypeRepository, never()).save(any());
    }
}
