package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.exception.InvalidAcademicPeriodUpdateException;
import ar.edu.utn.frc.siga.academic.mapper.AcademicPeriodMapperImpl;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.validator.AcademicPeriodUpdateValidator;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AcademicPeriodServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicPeriodServiceImpl(academicPeriodRepository, new AcademicPeriodMapperImpl(),
                new AcademicPeriodUpdateValidator(), eventPublisher);
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

    @Test
    @DisplayName("update: setear el receso en el ANUAL sincroniza el fin de 1C y el inicio de 2C y publica el cambio")
    void updateAnnualRecessSyncsTerms() {
        AcademicPeriod annual = AcademicPeriod.builder()
                .id(1L).year(2026).semester(0)
                .startDate(LocalDate.of(2026, 3, 16)).endDate(LocalDate.of(2026, 11, 30))
                .build();
        AcademicPeriod firstTerm = AcademicPeriod.builder()
                .id(2L).year(2026).semester(1)
                .startDate(LocalDate.of(2026, 3, 16)).endDate(LocalDate.of(2026, 7, 31))
                .build();
        AcademicPeriod secondTerm = AcademicPeriod.builder()
                .id(3L).year(2026).semester(2)
                .startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 11, 30))
                .build();
        when(academicPeriodRepository.findActiveById(1L)).thenReturn(Optional.of(annual));
        when(academicPeriodRepository.findByYearAndSemester(2026, 1)).thenReturn(Optional.of(firstTerm));
        when(academicPeriodRepository.findByYearAndSemester(2026, 2)).thenReturn(Optional.of(secondTerm));

        service.update(1L, new UpdateAcademicPeriodRequestDto(null, LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 27)));

        assertThat(annual.getRecessStart()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(firstTerm.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(secondTerm.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 28));
        verify(eventPublisher, org.mockito.Mockito.times(3))
                .publishEvent(any(ar.edu.utn.frc.siga.academic.event.AcademicPeriodChanged.class));
    }

    @Test
    @DisplayName("update: pedir receso en un período no ANUAL es 422")
    void updateRecessOnNonAnnualRejected() {
        AcademicPeriod firstTerm = AcademicPeriod.builder()
                .id(2L).year(2026).semester(1)
                .startDate(LocalDate.now().plusDays(30)).endDate(LocalDate.of(2026, 7, 31))
                .build();
        when(academicPeriodRepository.findActiveById(2L)).thenReturn(Optional.of(firstTerm));

        assertThatThrownBy(() -> service.update(2L, new UpdateAcademicPeriodRequestDto(
                null, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 27))))
                .isInstanceOf(InvalidAcademicPeriodUpdateException.class);
        verify(academicPeriodRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: cambiar la fecha de inicio cuando ya ocurrió es 422")
    void updateStartDateInPastRejected() {
        AcademicPeriod annual = AcademicPeriod.builder()
                .id(1L).year(2026).semester(0)
                .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusMonths(6))
                .build();
        when(academicPeriodRepository.findActiveById(1L)).thenReturn(Optional.of(annual));

        assertThatThrownBy(() -> service.update(1L, new UpdateAcademicPeriodRequestDto(
                LocalDate.now().plusDays(5), LocalDate.now().plusMonths(6), null, null)))
                .isInstanceOf(InvalidAcademicPeriodUpdateException.class);
    }

    @Test
    @DisplayName("findCurrent: es lectura pura, no materializa nada aunque falte el año en curso")
    void findCurrentDoesNotWrite() {
        when(academicPeriodRepository.findAllActive()).thenReturn(List.of());

        service.findCurrent();

        verify(academicPeriodRepository, never()).save(any());
        verify(academicPeriodRepository, never()).findByYearAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("materializeCurrentYear: sin filas del año en curso, las genera desde el año previo ajustadas a la semana")
    void materializeCurrentYearRollsOverFromPreviousYear() {
        int thisYear = LocalDate.now().getYear();
        AcademicPeriod previousAnnual = AcademicPeriod.builder()
                .id(1L).year(thisYear - 1).semester(0)
                .startDate(LocalDate.of(thisYear - 1, 3, 9)).endDate(LocalDate.of(thisYear - 1, 11, 30))
                .build();
        when(academicPeriodRepository.findByYearAndDeletedAtIsNull(thisYear)).thenReturn(List.of());
        when(academicPeriodRepository.findByYearAndDeletedAtIsNull(thisYear - 1)).thenReturn(List.of(previousAnnual));
        when(academicPeriodRepository.findByYearAndSemester(thisYear, 0)).thenReturn(Optional.empty());
        when(academicPeriodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.materializeCurrentYear();

        ArgumentCaptor<AcademicPeriod> captor = ArgumentCaptor.forClass(AcademicPeriod.class);
        verify(academicPeriodRepository).save(captor.capture());
        AcademicPeriod materialized = captor.getValue();
        assertThat(materialized.getYear()).isEqualTo(thisYear);
        assertThat(materialized.getSemester()).isEqualTo(0);
        assertThat(materialized.getStartDate().getYear()).isEqualTo(thisYear);
        assertThat(materialized.getStartDate().getMonthValue()).isEqualTo(3);
        assertThat(materialized.getStartDate().getDayOfWeek())
                .isEqualTo(LocalDate.of(thisYear - 1, 3, 9).getDayOfWeek());
    }

    @Test
    @DisplayName("materializeCurrentYear: si otra transacción concurrente ya insertó, la violación de integridad se ignora")
    void materializeCurrentYearToleratesRace() {
        int thisYear = LocalDate.now().getYear();
        AcademicPeriod previousAnnual = AcademicPeriod.builder()
                .id(1L).year(thisYear - 1).semester(0)
                .startDate(LocalDate.of(thisYear - 1, 3, 9)).endDate(LocalDate.of(thisYear - 1, 11, 30))
                .build();
        when(academicPeriodRepository.findByYearAndDeletedAtIsNull(thisYear)).thenReturn(List.of());
        when(academicPeriodRepository.findByYearAndDeletedAtIsNull(thisYear - 1)).thenReturn(List.of(previousAnnual));
        when(academicPeriodRepository.findByYearAndSemester(thisYear, 0)).thenReturn(Optional.empty());
        when(academicPeriodRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq (anio, cuatrimestre)"));

        assertThatCode(() -> service.materializeCurrentYear()).doesNotThrowAnyException();
    }
}
