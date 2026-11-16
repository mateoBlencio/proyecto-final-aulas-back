package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.space.dto.response.AffectedRoomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.model.DeactivationTarget;
import ar.edu.utn.frc.siga.space.service.ClassroomDeactivationHandler;
import ar.edu.utn.frc.siga.space.service.command.ClassroomDeactivation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomDeactivationCoordinator")
class ClassroomDeactivationCoordinatorTest {

    @Mock
    private ObjectProvider<ClassroomDeactivationHandler> provider;
    @Mock
    private ClassroomDeactivationHandler allocationHandler;
    @Mock
    private ClassroomDeactivationHandler roomRequestHandler;

    private ClassroomDeactivationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ClassroomDeactivationCoordinator(provider);
        lenient().when(provider.orderedStream()).thenAnswer(inv -> Stream.of(allocationHandler, roomRequestHandler));
    }

    private static AffectedRoomRequestDto affected(long itemId) {
        return new AffectedRoomRequestDto(itemId, itemId + 100, "NEW", "Ada Lovelace", "Fisica I", List.of(1L));
    }

    @Test
    @DisplayName("apply suma los tres contadores y concatena las listas de los dos handlers")
    void applySumaYConcatena() {
        ClassroomDeactivation deactivation = new ClassroomDeactivation(Set.of(1L, 2L), DeactivationTarget.BUILDING);
        when(allocationHandler.apply(deactivation)).thenReturn(new DeactivationImpactDto(3, 2, 0, List.of()));
        when(roomRequestHandler.apply(deactivation))
                .thenReturn(new DeactivationImpactDto(0, 0, 2, List.of(affected(10L), affected(11L))));

        DeactivationImpactDto impact = coordinator.apply(DeactivationTarget.BUILDING, Set.of(1L, 2L));

        assertThat(impact.releasedClasses()).isEqualTo(3);
        assertThat(impact.mergedClasses()).isEqualTo(2);
        assertThat(impact.affectedRoomRequests()).isEqualTo(2);
        assertThat(impact.visibleRoomRequests()).containsExactly(affected(10L), affected(11L));
    }

    @Test
    @DisplayName("preview llama a preview de cada handler y nunca a apply")
    void previewUsaPreview() {
        ClassroomDeactivation deactivation = new ClassroomDeactivation(Set.of(7L), DeactivationTarget.CLASSROOM);
        when(allocationHandler.preview(deactivation)).thenReturn(new DeactivationImpactDto(1, 0, 0, List.of()));
        when(roomRequestHandler.preview(deactivation)).thenReturn(new DeactivationImpactDto(0, 0, 1, List.of()));

        DeactivationImpactDto impact = coordinator.preview(DeactivationTarget.CLASSROOM, Set.of(7L));

        assertThat(impact).isEqualTo(new DeactivationImpactDto(1, 0, 1, List.of()));
        verify(allocationHandler).preview(deactivation);
        verify(roomRequestHandler).preview(deactivation);
        verify(allocationHandler, never()).apply(deactivation);
    }

    @Test
    @DisplayName("con ids vacios devuelve EMPTY y no llama a ningun handler")
    void idsVaciosNoLlamanAHandlers() {
        assertThat(coordinator.apply(DeactivationTarget.BUILDING, Set.of())).isSameAs(DeactivationImpactDto.EMPTY);
        assertThat(coordinator.preview(DeactivationTarget.BUILDING, Set.of())).isSameAs(DeactivationImpactDto.EMPTY);

        verifyNoInteractions(allocationHandler, roomRequestHandler);
    }

    @Test
    @DisplayName("sin handlers registrados devuelve EMPTY")
    void sinHandlers() {
        when(provider.orderedStream()).thenAnswer(inv -> Stream.empty());

        assertThat(coordinator.apply(DeactivationTarget.CLASSROOM, Set.of(1L))).isEqualTo(DeactivationImpactDto.EMPTY);
    }
}
