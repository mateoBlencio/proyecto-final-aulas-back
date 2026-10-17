package ar.edu.utn.frc.siga.buildingactivity.service.impl;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.dto.response.BuildingActivityDto;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivitySeen;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivitySeenRepository;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildingActivityServiceImpl")
@SuppressWarnings({"unchecked", "rawtypes"})
class BuildingActivityServiceImplTest {

    private static final String ME = "aux@utn.edu.ar";
    private static final String LINES = "[{\"occurrenceId\":10,\"eventLabel\":\"Fisica I\",\"date\":\"2026-10-20\","
            + "\"startTime\":\"08:00:00\",\"endTime\":\"09:30:00\",\"fromClassroom\":null,"
            + "\"toClassroom\":{\"id\":5,\"roomNumber\":101,\"buildingId\":1,\"buildingName\":\"Central\"}}]";

    @Mock
    private BuildingActivityRepository repository;
    @Mock
    private BuildingActivitySeenRepository seenRepository;
    @Mock
    private BuildingScopeResolver buildingScopeResolver;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private BuildingActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BuildingActivityServiceImpl(repository, seenRepository, buildingScopeResolver, objectMapper);
    }

    @Test
    @DisplayName("markSeen: delega en el upsert con el usuario y el id recibido")
    void markSeenDelegatesToAdvance() {
        service.markSeen(ME, 901L);

        verify(seenRepository).advance(ME, 901L);
    }

    @Test
    @DisplayName("find: seen es true si el id esta bajo la marca de agua o si el actor es el propio usuario")
    void findMarksSeenByWatermarkOrOwnActor() {
        stubScope(BuildingScope.unrestricted());
        stubWatermark(5L);
        when(repository.findAll(ArgumentMatchers.<Specification<BuildingActivity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        activity(7L, "otro@utn.edu.ar"),
                        activity(6L, "otro@utn.edu.ar"),
                        activity(5L, "otro@utn.edu.ar"),
                        activity(8L, ME))));

        Page<BuildingActivityDto> page = service.find(ME, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(BuildingActivityDto::id, BuildingActivityDto::seen)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(7L, false),
                        org.assertj.core.groups.Tuple.tuple(6L, false),
                        org.assertj.core.groups.Tuple.tuple(5L, true),
                        org.assertj.core.groups.Tuple.tuple(8L, true));
    }

    @Test
    @DisplayName("find: sin marca de agua guardada lo ajeno figura como no visto; deserializa las lineas del jsonb")
    void findWithoutWatermarkTreatsOthersAsUnseenAndParsesLines() {
        stubScope(BuildingScope.unrestricted());
        when(seenRepository.findById(ME)).thenReturn(Optional.empty());
        when(repository.findAll(ArgumentMatchers.<Specification<BuildingActivity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity(1L, "otro@utn.edu.ar"))));

        BuildingActivityDto dto = service.find(ME, null, null, null, PageRequest.of(0, 20)).getContent().getFirst();

        assertThat(dto.seen()).isFalse();
        assertThat(dto.building()).isEqualTo(new BuildingActivityDto.BuildingRef(1L, "Central"));
        assertThat(dto.lines()).singleElement().satisfies(line -> {
            assertThat(line.occurrenceId()).isEqualTo(10L);
            assertThat(line.date()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(line.fromClassroom()).isNull();
            assertThat(line.toClassroom().roomNumber()).isEqualTo(101);
        });
    }

    @Test
    @DisplayName("find: ordena por id descendente y limita el tamano de pagina a 100")
    void findSortsByIdDescAndCapsPageSize() {
        stubScope(BuildingScope.unrestricted());
        stubWatermark(0L);
        when(repository.findAll(ArgumentMatchers.<Specification<BuildingActivity>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.find(ME, null, null, null, PageRequest.of(2, 500, Sort.by("buildingName")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(ArgumentMatchers.<Specification<BuildingActivity>>any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    @DisplayName("find: buildingId fuera de alcance propaga el AccessDeniedException y no consulta el repositorio")
    void findWithBuildingOutOfScopePropagatesAccessDenied() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("sin acceso"))
                .when(buildingScopeResolver).requireAccess(Permission.BUILDING_ACTIVITY_READ, 9L);

        assertThatThrownBy(() -> service.find(ME, 9L, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(repository, seenRepository);
    }

    @Test
    @DisplayName("find: buildingId dentro de alcance valida el acceso y filtra por ese edificio")
    void findWithBuildingInScopeFiltersByBuilding() {
        stubScope(BuildingScope.unrestricted());
        stubWatermark(0L);
        when(repository.findAll(ArgumentMatchers.<Specification<BuildingActivity>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.find(ME, 3L, null, null, PageRequest.of(0, 20));

        verify(buildingScopeResolver).requireAccess(Permission.BUILDING_ACTIVITY_READ, 3L);
        Fixture fixture = evaluate(capturedFindSpec());
        verify(fixture.cb()).equal(fixture.path("buildingId"), 3L);
    }

    @Test
    @DisplayName("find: from incluye el dia completo desde 00:00 UTC y to excluye el 00:00 UTC del dia siguiente")
    void findDateRangeUsesUtcDayBoundaries() {
        stubScope(BuildingScope.unrestricted());
        stubWatermark(0L);
        when(repository.findAll(ArgumentMatchers.<Specification<BuildingActivity>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.find(ME, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), PageRequest.of(0, 20));

        Fixture fixture = evaluate(capturedFindSpec());
        verify(fixture.cb()).greaterThanOrEqualTo(fixture.path("occurredAt"), Instant.parse("2026-10-01T00:00:00Z"));
        verify(fixture.cb()).lessThan(fixture.path("occurredAt"), Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    @DisplayName("countUnseen: cuenta ids por encima de la marca de agua excluyendo al propio actor")
    void countUnseenFiltersByWatermarkAndExcludesOwnActor() {
        stubScope(BuildingScope.unrestricted());
        stubWatermark(5L);
        when(repository.count(ArgumentMatchers.<Specification<BuildingActivity>>any())).thenReturn(3L);

        long count = service.countUnseen(ME);

        assertThat(count).isEqualTo(3L);
        ArgumentCaptor<Specification<BuildingActivity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).count(captor.capture());
        Fixture fixture = evaluate(captor.getValue());
        verify(fixture.cb()).greaterThan(fixture.path("id"), 5L);
        verify(fixture.cb()).notEqual(fixture.path("actor"), ME);
    }

    @Test
    @DisplayName("countUnseen: sin marca de agua guardada usa 0 como piso")
    void countUnseenWithoutWatermarkStartsFromZero() {
        stubScope(BuildingScope.unrestricted());
        when(seenRepository.findById(ME)).thenReturn(Optional.empty());
        when(repository.count(ArgumentMatchers.<Specification<BuildingActivity>>any())).thenReturn(0L);

        service.countUnseen(ME);

        ArgumentCaptor<Specification<BuildingActivity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).count(captor.capture());
        Fixture fixture = evaluate(captor.getValue());
        verify(fixture.cb()).greaterThan(fixture.path("id"), 0L);
    }

    @Test
    @DisplayName("countUnseen: el alcance restringe a los edificios del usuario")
    void countUnseenRestrictsToUserBuildings() {
        stubScope(BuildingScope.of(Set.of(1L, 2L)));
        stubWatermark(0L);
        when(repository.count(ArgumentMatchers.<Specification<BuildingActivity>>any())).thenReturn(0L);

        service.countUnseen(ME);

        ArgumentCaptor<Specification<BuildingActivity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).count(captor.capture());
        Fixture fixture = evaluate(captor.getValue());
        verify(fixture.path("buildingId")).in(Set.of(1L, 2L));
    }

    @Test
    @DisplayName("countUnseen: sin edificios en alcance la specification no matchea nada")
    void countUnseenWithDeniedScopeMatchesNothing() {
        stubScope(BuildingScope.denied());
        stubWatermark(0L);
        when(repository.count(ArgumentMatchers.<Specification<BuildingActivity>>any())).thenReturn(0L);

        service.countUnseen(ME);

        ArgumentCaptor<Specification<BuildingActivity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).count(captor.capture());
        Fixture fixture = evaluate(captor.getValue());
        verify(fixture.cb()).disjunction();
        verify(fixture.cb(), never()).conjunction();
    }

    private void stubScope(BuildingScope scope) {
        when(buildingScopeResolver.scopeFor(Permission.BUILDING_ACTIVITY_READ)).thenReturn(scope);
    }

    private void stubWatermark(long watermark) {
        BuildingActivitySeen seen = new BuildingActivitySeenStub(watermark);
        when(seenRepository.findById(ME)).thenReturn(Optional.of(seen));
    }

    private Specification<BuildingActivity> capturedFindSpec() {
        ArgumentCaptor<Specification<BuildingActivity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private Fixture evaluate(Specification<BuildingActivity> spec) {
        Fixture fixture = new Fixture();
        spec.toPredicate(fixture.root, mock(CriteriaQuery.class), fixture.cb);
        return fixture;
    }

    private BuildingActivity activity(Long id, String actor) {
        return BuildingActivity.builder()
                .id(id)
                .buildingId(1L)
                .buildingName("Central")
                .operationId("op-" + id)
                .actor(actor)
                .source(AllocationSource.MANUAL)
                .reason("obra")
                .occurredAt(Instant.parse("2026-10-17T12:00:00Z"))
                .lines(LINES)
                .build();
    }

    private static final class BuildingActivitySeenStub extends BuildingActivitySeen {

        private BuildingActivitySeenStub(long watermark) {
            ReflectionTestUtils.setField(this, "lastSeenActivityId", watermark);
        }
    }

    private static final class Fixture {

        private final Root root = mock(Root.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
        private final java.util.Map<String, Path> paths = new java.util.HashMap<>();

        private Fixture() {
            when(root.get(any(String.class))).thenAnswer(inv -> paths.computeIfAbsent(inv.getArgument(0), k -> mock(Path.class)));
        }

        private CriteriaBuilder cb() {
            return cb;
        }

        private Path path(String name) {
            return paths.computeIfAbsent(name, k -> mock(Path.class));
        }
    }
}
