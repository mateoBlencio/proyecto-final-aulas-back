package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudyPlanResolver")
class StudyPlanResolverTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    private StudyPlanResolver resolver() {
        return new StudyPlanResolver(specialtyRepository, studyPlanRepository);
    }

    @Test
    @DisplayName("findOrCreate: vacío si falta especialidad o plan")
    void emptyWhenKeyIncomplete() {
        assertThat(resolver().findOrCreate(null, 94, Instant.now())).isEmpty();
        assertThat(resolver().findOrCreate(17, null, Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("findOrCreate: vacío si la especialidad no está sincronizada")
    void emptyWhenSpecialtyUnresolved() {
        when(specialtyRepository.findBySpecialtyCode(17)).thenReturn(Optional.empty());

        assertThat(resolver().findOrCreate(17, 94, Instant.now())).isEmpty();
        verify(studyPlanRepository, never()).findByPlanCodeAndSpecialty(any(), any());
    }

    @Test
    @DisplayName("findOrCreate: crea el StudyPlan cuando no existe para la especialidad+plan")
    void createsStudyPlanWhenMissing() {
        Specialty specialty = Specialty.builder().id(1L).specialtyCode(17).build();
        Instant syncedAt = Instant.now();
        when(specialtyRepository.findBySpecialtyCode(17)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialty(94, specialty)).thenReturn(Optional.empty());
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<StudyPlan> result = resolver().findOrCreate(17, 94, syncedAt);

        assertThat(result).isPresent();
        ArgumentCaptor<StudyPlan> saved = ArgumentCaptor.forClass(StudyPlan.class);
        verify(studyPlanRepository).save(saved.capture());
        assertThat(saved.getValue().getPlanCode()).isEqualTo(94);
        assertThat(saved.getValue().getSpecialty()).isEqualTo(specialty);
        assertThat(saved.getValue().getSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    @DisplayName("findOrCreate: reutiliza el StudyPlan existente sin volver a guardarlo")
    void reusesExistingStudyPlan() {
        Specialty specialty = Specialty.builder().id(1L).specialtyCode(17).build();
        StudyPlan existing = StudyPlan.builder().id(5L).planCode(94).specialty(specialty).build();
        when(specialtyRepository.findBySpecialtyCode(17)).thenReturn(Optional.of(specialty));
        when(studyPlanRepository.findByPlanCodeAndSpecialty(94, specialty)).thenReturn(Optional.of(existing));

        Optional<StudyPlan> result = resolver().findOrCreate(17, 94, Instant.now());

        assertThat(result).contains(existing);
        verify(studyPlanRepository, never()).save(any());
    }
}
