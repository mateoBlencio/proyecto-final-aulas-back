package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.CommissionMapper;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionServiceImpl")
class CommissionServiceImplTest {

    @Mock
    private CommissionRepository commissionRepository;
    @Mock
    private AcademicPeriodRepository academicPeriodRepository;
    @Mock
    private CommissionMapper commissionMapper;

    private CommissionServiceImpl service;

    private final AcademicPeriod period = AcademicPeriod.builder().id(1L).year(2026).semester(1).active(true).build();

    @BeforeEach
    void setUp() {
        service = new CommissionServiceImpl(commissionRepository, academicPeriodRepository, commissionMapper);
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando la comisión existe")
    void findByIdReturnsMappedDto() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").commissionNumber(1)
                .academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", 1, null, null);
        when(commissionRepository.findById(3L)).thenReturn(Optional.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        CommissionResponseDto result = service.findById(3L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si la comisión no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingCommissionThrowsResourceNotFound() {
        when(commissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Commission not found with id: 99");
    }

    @Test
    @DisplayName("findByIds: mapea cada comisión encontrada")
    void findByIdsMapsAllFound() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").commissionNumber(1)
                .academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", 1, null, null);
        when(commissionRepository.findAllById(List.of(3L))).thenReturn(List.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        List<CommissionResponseDto> result = service.findByIds(List.of(3L));

        assertThat(result).containsExactly(dto);
    }

    @Test
    @DisplayName("findOrCreate: si la comisión ya existe para ese período, no la crea y created queda en false")
    void findOrCreateWithExistingCommissionDoesNotSave() {
        Commission existing = Commission.builder().id(3L).courseCode("K1001").commissionNumber(1)
                .academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", 1, 1, null);
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(period));
        when(commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriod("K1001", 1, period))
                .thenReturn(Optional.of(existing));
        when(commissionMapper.toDto(existing)).thenReturn(dto);

        FindOrCreateResult<CommissionResponseDto> result =
                service.findOrCreate("K1001", 1, 1, 2026, 1);

        assertThat(result.created()).isFalse();
        assertThat(result.value()).isEqualTo(dto);
        verify(commissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: si no existe, crea la comisión con courseCode/commissionNumber/yearLevel/period y created queda en true")
    void findOrCreateWithoutExistingCommissionCreatesWithGivenFields() {
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(period));
        when(commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriod("K1001", 1, period))
                .thenReturn(Optional.empty());
        when(commissionRepository.save(any())).thenAnswer(invocation -> {
            Commission toSave = invocation.getArgument(0);
            toSave.setId(8L);
            return toSave;
        });
        when(commissionMapper.toDto(any())).thenReturn(new CommissionResponseDto(8L, "K1001", 1, 1, null));

        FindOrCreateResult<CommissionResponseDto> result =
                service.findOrCreate("K1001", 1, 1, 2026, 1);

        assertThat(result.created()).isTrue();

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        Commission saved = captor.getValue();
        assertThat(saved.getCourseCode()).isEqualTo("K1001");
        assertThat(saved.getCommissionNumber()).isEqualTo(1);
        assertThat(saved.getYearLevel()).isEqualTo(1);
        assertThat(saved.getAcademicPeriod()).isEqualTo(period);
    }

    @Test
    @DisplayName("findOrCreate: si el período académico no existe, lanza ResourceNotFoundException con la clave year-semester")
    void findOrCreateWithMissingPeriodThrowsResourceNotFound() {
        when(academicPeriodRepository.findByYearAndSemester(2030, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreate("K1001", 1, 1, 2030, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("AcademicPeriod not found with id: 2030-2");
        verify(commissionRepository, never()).findByCourseCodeAndCommissionNumberAndAcademicPeriod(any(), any(), any());
    }
}
