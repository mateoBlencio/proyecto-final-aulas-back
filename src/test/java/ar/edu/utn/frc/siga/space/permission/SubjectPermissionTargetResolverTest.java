package ar.edu.utn.frc.siga.space.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("resolveNames: resuelve id -> nombre en batch")
    void resolveNamesBatch() {
        when(subjectService.findByIds(Set.of(1L, 2L))).thenReturn(List.of(
                new SubjectResponseDto(1L, 100, "Análisis Matemático", "ANUAL", null),
                new SubjectResponseDto(2L, 200, "Física I", "ANUAL", null)));

        assertThat(new SubjectPermissionTargetResolver(subjectService).resolveNames(Set.of(1L, 2L)))
                .containsEntry(1L, "Análisis Matemático")
                .containsEntry(2L, "Física I");
    }

    @Test
    @DisplayName("resolveNames: colección vacía no llama al service")
    void resolveNamesEmptySkipsService() {
        assertThat(new SubjectPermissionTargetResolver(subjectService).resolveNames(Set.of())).isEmpty();
        verifyNoInteractions(subjectService);
    }
}
