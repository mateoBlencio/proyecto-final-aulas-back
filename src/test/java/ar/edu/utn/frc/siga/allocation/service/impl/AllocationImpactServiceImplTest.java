package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationImpactServiceImpl.permittedRooms")
class AllocationImpactServiceImplTest {

    @Mock private AllocationTargetResolver targetResolver;
    @Mock private AllocationValidator validator;
    @Mock private AllocationOccupancyReader occupancyReader;
    @Mock private AllocationRepository allocationRepository;
    @Mock private ClassroomService classroomService;
    @Mock private AcademicEventService academicEventService;
    @Mock private BuildingScopeResolver buildingScopeResolver;

    private AllocationImpactServiceImpl service;

    private final ClassroomResponseDto openRoom =
            new ClassroomResponseDto(1L, 101, 40, 10L, "Central", 1L, "Aula");
    private final ClassroomResponseDto subsetRoom =
            new ClassroomResponseDto(2L, 102, 40, 10L, "Central", 1L, "Aula");
    private final ClassroomResponseDto noneRoom =
            new ClassroomResponseDto(3L, 103, 40, 10L, "Central", 1L, "Aula");

    private final Map<Long, ClassroomSubjectPermissionDto> permissionByRoom = Map.of(
            1L, new ClassroomSubjectPermissionDto(1L, true, Set.of()),
            2L, new ClassroomSubjectPermissionDto(2L, false, Set.of(500L)),
            3L, new ClassroomSubjectPermissionDto(3L, false, Set.of()));

    @BeforeEach
    void setUp() {
        service = new AllocationImpactServiceImpl(targetResolver, validator, occupancyReader,
                allocationRepository, classroomService, academicEventService, buildingScopeResolver);
    }

    @Test
    @DisplayName("con materia: sólo quedan las aulas que la habilitan (SUBSET sin la materia y NONE se descartan)")
    void filtersOutRoomsThatDoNotPermitTheSubject() {
        List<ClassroomResponseDto> result = service.permittedRooms(
                List.of(openRoom, subsetRoom, noneRoom), permissionByRoom, 999L);

        assertThat(result).extracting(ClassroomResponseDto::id).containsExactly(1L);
    }

    @Test
    @DisplayName("con materia habilitada en el SUBSET: el aula SUBSET queda")
    void keepsSubsetRoomWhenSubjectMatches() {
        List<ClassroomResponseDto> result = service.permittedRooms(
                List.of(openRoom, subsetRoom, noneRoom), permissionByRoom, 500L);

        assertThat(result).extracting(ClassroomResponseDto::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("sin materia (subjectId null): no filtra, devuelve todas")
    void doesNotFilterWhenSubjectIsNull() {
        List<ClassroomResponseDto> result = service.permittedRooms(
                List.of(openRoom, subsetRoom, noneRoom), permissionByRoom, null);

        assertThat(result).extracting(ClassroomResponseDto::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("aula sin entrada de permiso: se interpreta como permitida")
    void treatsMissingPermissionAsPermitted() {
        List<ClassroomResponseDto> result = service.permittedRooms(
                List.of(openRoom, subsetRoom), Map.of(), 999L);

        assertThat(result).extracting(ClassroomResponseDto::id).containsExactlyInAnyOrder(1L, 2L);
    }
}
