package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationTargetResolver: liberadas por baja de aula")
class AllocationTargetResolverDeactivationTest {

    private static final long CLASSROOM_ID = 12L;

    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationValidator validator;

    private AllocationTargetResolver resolver;

    private OccurrenceSlotDto libre;
    private OccurrenceSlotDto porBaja;
    private OccurrenceSlotDto virtual;
    private OccurrenceSlotDto espejoRetirado;

    @BeforeEach
    void setUp() {
        resolver = new AllocationTargetResolver(occurrenceService, validator);
        libre = slot(1L, OccurrenceStatus.NEEDS_ROOM, null);
        porBaja = slot(2L, OccurrenceStatus.ROOM_RELEASED, ReleaseReason.CLASSROOM_DEACTIVATED);
        virtual = slot(3L, OccurrenceStatus.ROOM_RELEASED, ReleaseReason.VIRTUAL_CLASS);
        espejoRetirado = slot(4L, OccurrenceStatus.ROOM_RELEASED, ReleaseReason.MIRROR_RETIRED);
        when(occurrenceService.findSlots(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(libre, porBaja, virtual, espejoRetirado));
    }

    private static OccurrenceSlotDto slot(long id, OccurrenceStatus status, ReleaseReason reason) {
        return new OccurrenceSlotDto(id, 55L, LocalDate.now().plusDays(5), LocalTime.of(8, 0), LocalTime.of(10, 0),
                status, 30, 1, reason, null, null, null);
    }

    private List<AllocationItem> items() {
        return List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(1L, 2L, 3L, 4L)), CLASSROOM_ID));
    }

    @Test
    @DisplayName("con el flag en true: la liberada por baja entra al mapa y a reactivated; VIRTUAL_CLASS y MIRROR_RETIRED se saltean")
    void conFlagReactivaSoloLaDeBaja() {
        AllocationTargetResolver.ResolvedAssignments resolved =
                resolver.resolveClassroomByOccurrence(items(), LocalDate.now(), true);

        assertThat(resolved.classroomByOccurrence()).containsOnlyKeys(libre, porBaja);
        assertThat(resolved.reactivated()).containsExactly(porBaja);
        assertThat(resolved.skippedReleased()).containsExactlyInAnyOrder(virtual, espejoRetirado);
    }

    @Test
    @DisplayName("con el flag en false: todas las liberadas van a skippedReleased y no se reactiva ninguna")
    void sinFlagSalteaTodasLasLiberadas() {
        AllocationTargetResolver.ResolvedAssignments resolved =
                resolver.resolveClassroomByOccurrence(items(), LocalDate.now(), false);

        assertThat(resolved.classroomByOccurrence()).containsOnlyKeys(libre);
        assertThat(resolved.reactivated()).isEmpty();
        assertThat(resolved.skippedReleased()).containsExactlyInAnyOrder(porBaja, virtual, espejoRetirado);
    }
}
