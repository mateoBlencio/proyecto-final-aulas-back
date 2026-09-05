package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectCommissionMapper;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectCommissionServiceImpl")
class SubjectCommissionServiceImplTest {

    @Mock
    private SubjectCommissionRepository subjectCommissionRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private CommissionRepository commissionRepository;
    @Mock
    private SubjectCommissionMapper subjectCommissionMapper;

    private SubjectCommissionServiceImpl service;

    private final Subject subject = Subject.builder().id(1L).code(101).name("Algoritmos").build();
    private final Commission commission = Commission.builder().id(2L).courseCode("K1001").build();

    @BeforeEach
    void setUp() {
        service = new SubjectCommissionServiceImpl(
                subjectCommissionRepository, subjectRepository, commissionRepository, subjectCommissionMapper);
    }

    @Test
    @DisplayName("find: si existe la relación, devuelve el DTO mapeado")
    void findWithExistingRelationReturnsMappedDto() {
        SubjectCommission existing = SubjectCommission.builder().subject(subject).commission(commission)
                .enrolledCount(30).build();
        SubjectCommissionResponseDto dto = new SubjectCommissionResponseDto(1L, 2L, null, 30);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(commissionRepository.findById(2L)).thenReturn(Optional.of(commission));
        when(subjectCommissionRepository.findBySubjectAndCommissionAndDeletedAtIsNull(subject, commission)).thenReturn(Optional.of(existing));
        when(subjectCommissionMapper.toDto(existing)).thenReturn(dto);

        SubjectCommissionResponseDto result = service.findBySubjectAndCommission(1L, 2L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("find: si no existe la relación, lanza ResourceNotFoundException")
    void findWithoutExistingRelationThrowsResourceNotFound() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(commissionRepository.findById(2L)).thenReturn(Optional.of(commission));
        when(subjectCommissionRepository.findBySubjectAndCommissionAndDeletedAtIsNull(subject, commission)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySubjectAndCommission(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subjectCommissionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("find: si el subject no existe, lanza ResourceNotFoundException")
    void findWithMissingSubjectThrowsResourceNotFound() {
        when(subjectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBySubjectAndCommission(99L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Subject not found with id: 99");
    }

    @Test
    @DisplayName("findByCommissionAndSubjectCode: si existe la relación, devuelve el DTO mapeado")
    void findByCommissionAndSubjectCodeWithExistingRelationReturnsMappedDto() {
        SubjectCommission existing = SubjectCommission.builder().subject(subject).commission(commission)
                .enrolledCount(30).build();
        SubjectCommissionResponseDto dto = new SubjectCommissionResponseDto(1L, 2L, null, 30);
        when(subjectCommissionRepository.findFirstByCommission_IdAndSubject_CodeAndDeletedAtIsNullOrderBySubject_IdAsc(2L, 101))
                .thenReturn(Optional.of(existing));
        when(subjectCommissionMapper.toDto(existing)).thenReturn(dto);

        SubjectCommissionResponseDto result = service.findByCommissionAndSubjectCode(2L, 101);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByCommissionAndSubjectCode: si no existe la relación, lanza ResourceNotFoundException")
    void findByCommissionAndSubjectCodeWithoutExistingRelationThrowsResourceNotFound() {
        when(subjectCommissionRepository.findFirstByCommission_IdAndSubject_CodeAndDeletedAtIsNullOrderBySubject_IdAsc(2L, 101))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCommissionAndSubjectCode(2L, 101))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAll: devuelve la página de relaciones mapeadas según el Specification")
    void findAllMapsAllRelations() {
        SubjectCommission relation = SubjectCommission.builder().subject(subject).commission(commission)
                .enrolledCount(30).build();
        SubjectCommissionResponseDto dto = new SubjectCommissionResponseDto(1L, 2L, null, 30);
        when(subjectCommissionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(relation)));
        when(subjectCommissionMapper.toDto(relation)).thenReturn(dto);

        assertThat(service.findAll(new SubjectCommissionFilter(null, null), Pageable.unpaged(), false).getContent())
                .containsExactly(dto);
    }

    @Test
    @DisplayName("findAll: con includeDeactivated=true, no restringe por deletedAt")
    void findAllWithIncludeDeactivatedMapsEveryStatus() {
        SubjectCommission active = SubjectCommission.builder().id(new SubjectCommissionId(1L, 2L))
                .subject(subject).commission(commission).enrolledCount(30).build();
        SubjectCommission inactive = SubjectCommission.builder().id(new SubjectCommissionId(1L, 3L))
                .subject(subject).commission(commission).enrolledCount(10).build();
        inactive.deactivate();
        SubjectCommissionResponseDto activeDto = new SubjectCommissionResponseDto(1L, 2L, null, 30);
        SubjectCommissionResponseDto inactiveDto = new SubjectCommissionResponseDto(1L, 2L, null, 10);
        when(subjectCommissionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(active, inactive)));
        when(subjectCommissionMapper.toDto(active)).thenReturn(activeDto);
        when(subjectCommissionMapper.toDto(inactive)).thenReturn(inactiveDto);

        assertThat(service.findAll(new SubjectCommissionFilter(null, null), Pageable.unpaged(), true).getContent())
                .containsExactly(activeDto, inactiveDto);
    }
}
