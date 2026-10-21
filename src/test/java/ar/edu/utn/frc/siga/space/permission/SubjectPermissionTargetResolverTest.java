package ar.edu.utn.frc.siga.space.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectPermissionTargetResolver")
class SubjectPermissionTargetResolverTest {

    @Mock
    private SubjectService subjectService;

    @Test
    @DisplayName("kind() es SUBJECT")
    void kindIsSubject() {
        assertThat(new SubjectPermissionTargetResolver(subjectService).kind())
                .isEqualTo(PermissionTargetKind.SUBJECT);
    }

    private SubjectPermissionTargetResolver resolver() {
        return new SubjectPermissionTargetResolver(subjectService);
    }

    @Test
    @DisplayName("resolve: materia con plan y especialidad completos arma el TargetInfo entero")
    void resolveFullSubject() {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(
                10L, 2023, new SpecialtyResponseDto(5, "Ingeniería en Sistemas", "ISI"), true);
        when(subjectService.findByIdsIncludingDeactivated(Set.of(1L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Análisis Matemático", "ANUAL", plan, true, null)));

        assertThat(resolver().resolve(Set.of(1L))).containsOnlyKeys(1L)
                .containsEntry(1L, new TargetInfo(
                        "Análisis Matemático", "Ingeniería en Sistemas", "ISI", 5, 2023));
    }

    @Test
    @DisplayName("resolve: resuelve varias materias en una sola consulta")
    void resolveBatch() {
        when(subjectService.findByIdsIncludingDeactivated(Set.of(1L, 2L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Análisis Matemático", "ANUAL", null, true, null),
                new SubjectResponseDto(2L, 200, "Física I", "ANUAL", null, true, null)));

        assertThat(resolver().resolve(Set.of(1L, 2L)))
                .containsEntry(1L, TargetInfo.ofName("Análisis Matemático"))
                .containsEntry(2L, TargetInfo.ofName("Física I"));
    }

    @Test
    @DisplayName("resolve: studyPlan null deja los datos de especialidad y plan en null")
    void resolveNullStudyPlan() {
        when(subjectService.findByIdsIncludingDeactivated(Set.of(1L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Física I", "ANUAL", null, true, null)));

        assertThat(resolver().resolve(Set.of(1L))).containsEntry(1L, TargetInfo.ofName("Física I"));
    }

    @Test
    @DisplayName("resolve: specialty null conserva el código de plan y deja la especialidad en null")
    void resolveNullSpecialty() {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(10L, 2023, null, true);
        when(subjectService.findByIdsIncludingDeactivated(Set.of(1L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Física I", "ANUAL", plan, true, null)));

        assertThat(resolver().resolve(Set.of(1L)))
                .containsEntry(1L, new TargetInfo("Física I", null, null, null, 2023));
    }

    @Test
    @DisplayName("resolve: materia desactivada se resuelve igual")
    void resolveDeactivatedSubject() {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(
                10L, 2023, new SpecialtyResponseDto(5, "Ingeniería en Sistemas", "ISI"), true);
        when(subjectService.findByIdsIncludingDeactivated(Set.of(1L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Física I", "ANUAL", plan, false, null)));

        assertThat(resolver().resolve(Set.of(1L)).get(1L))
                .isEqualTo(new TargetInfo("Física I", "Ingeniería en Sistemas", "ISI", 5, 2023));
    }

    @Test
    @DisplayName("resolve: id inexistente queda ausente del mapa")
    void resolveMissingIdAbsent() {
        when(subjectService.findByIdsIncludingDeactivated(Set.of(1L, 99L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Física I", "ANUAL", null, true, null)));

        assertThat(resolver().resolve(Set.of(1L, 99L))).containsOnlyKeys(1L).doesNotContainKey(99L);
    }

    @Test
    @DisplayName("resolve: colección vacía no llama al service")
    void resolveEmptySkipsService() {
        assertThat(resolver().resolve(Set.of())).isEmpty();
        verifyNoInteractions(subjectService);
    }
}
