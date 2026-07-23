package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.CommissionMapper;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @DisplayName("findById: devuelve el DTO mapeado cuando la comisiÃ³n existe")
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
    @DisplayName("findById: si la comisiÃ³n no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingCommissionThrowsResourceNotFound() {
        when(commissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Commission not found with id: 99");
    }

    @Test
    @DisplayName("findByIds: mapea cada comisiÃ³n encontrada")
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
    @DisplayName("find: si la comisiÃ³n existe para ese perÃ­odo, devuelve el DTO mapeado")
    void findWithExistingCommissionReturnsMappedDto() {
        Commission existing = Commission.builder().id(3L).courseCode("K1001").commissionNumber(1)
                .academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", 1, 1, null);
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(period));
        when(commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriod("K1001", 1, period))
                .thenReturn(Optional.of(existing));
        when(commissionMapper.toDto(existing)).thenReturn(dto);

        CommissionResponseDto result = service.findByCourseAndNumberAndPeriod("K1001", 1, 2026, 1);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("find: si no existe la comisiÃ³n para ese perÃ­odo, lanza ResourceNotFoundException")
    void findWithoutExistingCommissionThrowsResourceNotFound() {
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(period));
        when(commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriod("K1001", 1, period))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCourseAndNumberAndPeriod("K1001", 1, 2026, 1))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("find: si el perÃ­odo acadÃ©mico no existe, lanza ResourceNotFoundException con la clave year-semester")
    void findWithMissingPeriodThrowsResourceNotFound() {
        when(academicPeriodRepository.findByYearAndSemester(2030, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCourseAndNumberAndPeriod("K1001", 1, 2030, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("AcademicPeriod not found with id: 2030-2");
        verify(commissionRepository, never()).findByCourseCodeAndCommissionNumberAndAcademicPeriod(any(), any(), any());
    }

    @Test
    @DisplayName("findAll: mapea todas las comisiones del repositorio")
    void findAllMapsAllCommissions() {
        Commission commission = Commission.builder().id(3L).courseCode("K1001").commissionNumber(1)
                .academicPeriod(period).build();
        CommissionResponseDto dto = new CommissionResponseDto(3L, "K1001", 1, null, null);
        when(commissionRepository.findAll()).thenReturn(List.of(commission));
        when(commissionMapper.toDto(commission)).thenReturn(dto);

        List<CommissionResponseDto> result = service.findAll();

        assertThat(result).containsExactly(dto);
    }
}
