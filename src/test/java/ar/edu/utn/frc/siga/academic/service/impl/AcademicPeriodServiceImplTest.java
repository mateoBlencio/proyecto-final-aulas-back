package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.AcademicPeriodMapperImpl;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicPeriodServiceImpl")
class AcademicPeriodServiceImplTest {

    @Mock
    private AcademicPeriodRepository academicPeriodRepository;

    private AcademicPeriodServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicPeriodServiceImpl(academicPeriodRepository, new AcademicPeriodMapperImpl());
    }

    @Test
    @DisplayName("findOrCreate: si el período ya existe, no lo crea y created queda en false")
    void findOrCreateWithExistingPeriodDoesNotSave() {
        AcademicPeriod existing = AcademicPeriod.builder()
                .id(1L).year(2026).semester(1)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .build();
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(existing));

        FindOrCreateResult<AcademicPeriodResponseDto> result =
                service.findOrCreate(2026, TermType.PRIMER_CUATRIMESTRE);

        assertThat(result.created()).isFalse();
        assertThat(result.value().year()).isEqualTo(2026);
        assertThat(result.value().semester()).isEqualTo(1);
        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: si no existe, crea el período con las fechas del TermType y created queda en true")
    void findOrCreateWithoutExistingPeriodCreatesWithTermTypeDates() {
        when(academicPeriodRepository.findByYearAndSemester(2026, 2)).thenReturn(Optional.empty());
        when(academicPeriodRepository.save(any())).thenAnswer(invocation -> {
            AcademicPeriod toSave = invocation.getArgument(0);
            toSave.setId(9L);
            return toSave;
        });

        FindOrCreateResult<AcademicPeriodResponseDto> result =
                service.findOrCreate(2026, TermType.SEGUNDO_CUATRIMESTRE);

        assertThat(result.created()).isTrue();

        ArgumentCaptor<AcademicPeriod> captor = ArgumentCaptor.forClass(AcademicPeriod.class);
        verify(academicPeriodRepository).save(captor.capture());
        AcademicPeriod saved = captor.getValue();
        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getSemester()).isEqualTo(2);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));

        assertThat(result.value().startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.value().endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("findActive: devuelve solo los períodos activos, mapeando year/semester/startDate/endDate")
    void findActiveReturnsOnlyActivePeriodsMapped() {
        AcademicPeriod active = AcademicPeriod.builder()
                .id(1L).year(2026).semester(1)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .build();
        when(academicPeriodRepository.findAllActive()).thenReturn(List.of(active));

        List<AcademicPeriodResponseDto> result = service.findActive();

        assertThat(result).hasSize(1);
        AcademicPeriodResponseDto dto = result.getFirst();
        assertThat(dto.year()).isEqualTo(2026);
        assertThat(dto.semester()).isEqualTo(1);
        assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(dto.endDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("findActive: con endDate null en la entidad, el DTO también lo expone en null")
    void findActiveMapsNullEndDate() {
        AcademicPeriod active = AcademicPeriod.builder()
                .id(2L).year(2026).semester(0)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(null)
                .build();
        when(academicPeriodRepository.findAllActive()).thenReturn(List.of(active));

        List<AcademicPeriodResponseDto> result = service.findActive();

        assertThat(result.getFirst().endDate()).isNull();
    }

    @Test
    @DisplayName("findAll: sin includeDeactivated, mapea solo los períodos activos")
    void findAllMapsActivePeriods() {
        AcademicPeriod period = AcademicPeriod.builder()
                .id(1L).year(2026).semester(1)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .build();
        when(academicPeriodRepository.findAllActive()).thenReturn(List.of(period));

        List<AcademicPeriodResponseDto> result = service.findAll(false);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().year()).isEqualTo(2026);
    }

    @Test
    @DisplayName("findAll: con includeDeactivated=true, mapea todos los períodos del repositorio")
    void findAllWithIncludeDeactivatedMapsAllPeriods() {
        AcademicPeriod period = AcademicPeriod.builder()
                .id(1L).year(2026).semester(1)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .build();
        when(academicPeriodRepository.findAll()).thenReturn(List.of(period));

        List<AcademicPeriodResponseDto> result = service.findAll(true);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().year()).isEqualTo(2026);
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando el período existe")
    void findByIdReturnsMappedDto() {
        AcademicPeriod period = AcademicPeriod.builder()
                .id(1L).year(2026).semester(1)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .build();
        when(academicPeriodRepository.findActiveById(1L)).thenReturn(Optional.of(period));

        AcademicPeriodResponseDto result = service.findById(1L);

        assertThat(result.year()).isEqualTo(2026);
    }

    @Test
    @DisplayName("findById: si el período no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingPeriodThrowsResourceNotFound() {
        when(academicPeriodRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("AcademicPeriod not found with id: 99");
    }
}
