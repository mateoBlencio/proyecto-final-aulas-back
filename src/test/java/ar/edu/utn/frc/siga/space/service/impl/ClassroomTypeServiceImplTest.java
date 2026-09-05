package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomTypeMapper;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static ar.edu.utn.frc.siga.space.service.ClassroomService.DEFAULT_CLASSROOM_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomTypeServiceImpl")
class ClassroomTypeServiceImplTest {

    @Mock
    private ClassroomTypeRepository classroomTypeRepository;
    @Mock
    private ClassroomTypeMapper classroomTypeMapper;

    private ClassroomTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassroomTypeServiceImpl(classroomTypeRepository, classroomTypeMapper);
        lenient().when(classroomTypeMapper.toDto(any()))
                .thenReturn(new ClassroomTypeResponseDto(1L, "Laboratorio", true));
    }

    @Test
    @DisplayName("findById: devuelve el tipo de aula cuando existe")
    void findByIdReturnsExistingType() {
        ClassroomType type = SpaceTestData.classroomType().build();
        when(classroomTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));

        assertThat(service.findById(1L)).isEqualTo(type);
    }

    @Test
    @DisplayName("findById: si el tipo no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingTypeThrowsResourceNotFound() {
        when(classroomTypeRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("ClassroomType not found with id: 99");
    }

    @Test
    @DisplayName("create: persiste la descripción cuando no está duplicada")
    void createPersistsWhenNotDuplicate() {
        when(classroomTypeRepository.existsByDescriptionIgnoreCase("Laboratorio")).thenReturn(false);
        when(classroomTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new ClassroomTypeRequestDto("Laboratorio"));

        verify(classroomTypeRepository).save(any(ClassroomType.class));
    }

    @Test
    @DisplayName("create: descripción duplicada (incluye desactivados) lanza SpaceDomainException")
    void createWithDuplicateDescriptionThrows() {
        when(classroomTypeRepository.existsByDescriptionIgnoreCase("Laboratorio")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ClassroomTypeRequestDto("Laboratorio")))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("Laboratorio");
        verify(classroomTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: cambia la descripción cuando no colisiona con otro tipo")
    void updateChangesDescription() {
        ClassroomType type = SpaceTestData.classroomType().build();
        when(classroomTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));
        when(classroomTypeRepository.existsByDescriptionIgnoreCaseAndIdNot("Laboratorio", 1L)).thenReturn(false);
        when(classroomTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, new ClassroomTypeRequestDto("Laboratorio"));

        assertThat(type.getDescription()).isEqualTo("Laboratorio");
    }

    @Test
    @DisplayName("update: descripción usada por otro tipo lanza SpaceDomainException")
    void updateWithDuplicateDescriptionThrows() {
        ClassroomType type = SpaceTestData.classroomType().build();
        when(classroomTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));
        when(classroomTypeRepository.existsByDescriptionIgnoreCaseAndIdNot("Laboratorio", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new ClassroomTypeRequestDto("Laboratorio")))
                .isInstanceOf(SpaceDomainException.class);
        verify(classroomTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: renombrar el tipo por defecto lanza SpaceDomainException")
    void updateRenamingDefaultTypeThrows() {
        ClassroomType type = SpaceTestData.classroomType().description(DEFAULT_CLASSROOM_TYPE).build();
        when(classroomTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.update(1L, new ClassroomTypeRequestDto("Laboratorio")))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining(DEFAULT_CLASSROOM_TYPE);
        verify(classroomTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: guardar el tipo por defecto sin cambiarle el nombre no se bloquea")
    void updateDefaultTypeWithSameDescriptionIsAllowed() {
        ClassroomType type = SpaceTestData.classroomType().description(DEFAULT_CLASSROOM_TYPE).build();
        when(classroomTypeRepository.findActiveById(1L)).thenReturn(Optional.of(type));
        when(classroomTypeRepository.existsByDescriptionIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        when(classroomTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, new ClassroomTypeRequestDto(DEFAULT_CLASSROOM_TYPE.toUpperCase()));

        verify(classroomTypeRepository).save(type);
    }

    @Test
    @DisplayName("deactivate: desactivar el tipo por defecto lanza SpaceDomainException")
    void deactivateDefaultTypeThrows() {
        ClassroomType type = SpaceTestData.classroomType().description(DEFAULT_CLASSROOM_TYPE).build();
        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.deactivate(1L))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining(DEFAULT_CLASSROOM_TYPE);
        verify(classroomTypeRepository, never()).softDelete(any());
    }

    @Test
    @DisplayName("deactivate: un tipo que no es el default se desactiva normalmente")
    void deactivateRegularTypeSoftDeletes() {
        ClassroomType type = SpaceTestData.classroomType().description("Laboratorio").build();
        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        service.deactivate(1L);

        verify(classroomTypeRepository).softDelete(type);
    }
}
