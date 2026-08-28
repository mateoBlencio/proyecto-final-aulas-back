package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomTypeServiceImpl")
class ClassroomTypeServiceImplTest {

    @Mock
    private ClassroomTypeRepository classroomTypeRepository;

    private ClassroomTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassroomTypeServiceImpl(classroomTypeRepository);
    }

    @Test
    @DisplayName("findById: devuelve el tipo de aula cuando existe")
    void findByIdReturnsExistingType() {
        ClassroomType type = SpaceTestData.classroomType().build();
        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        assertThat(service.findById(1L)).isEqualTo(type);
    }

    @Test
    @DisplayName("findById: si el tipo no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingTypeThrowsResourceNotFound() {
        when(classroomTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("ClassroomType not found with id: 99");
    }
}
