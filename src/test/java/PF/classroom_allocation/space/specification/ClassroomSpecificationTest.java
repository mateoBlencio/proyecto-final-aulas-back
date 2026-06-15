package PF.classroom_allocation.space.specification;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.model.Classroom;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class ClassroomSpecificationTest {

    @Mock private Root<Classroom> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;

    @Captor private ArgumentCaptor<Predicate[]> predicatesCaptor;

    private Path mockPath(String attribute) {
        Path path = mock(Path.class);
        when(root.get(attribute)).thenReturn(path);
        return path;
    }

    @Test
    void withFilter_shouldAlwaysFilterDeletedFalse() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, null, null, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);

        Predicate result = spec.toPredicate(root, query, cb);

        verify(root).get("deleted");
        verify(cb).isFalse(deletedPath);
        assertNotNull(result);
    }

    @Test
    void withFilter_shouldAddRoomNumberPredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path roomNumberPath = mockPath("roomNumber");
        Predicate likePredicate = mock(Predicate.class);
        when(cb.lower(roomNumberPath)).thenReturn(roomNumberPath);
        when(cb.like(roomNumberPath, "%101%")).thenReturn(likePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter("101", null, null, null, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).like(roomNumberPath, "%101%");
    }

    @Test
    void withFilter_shouldAddBuildingIdPredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path buildingPath = mock(Path.class);
        when(root.get("building")).thenReturn(buildingPath);
        Path buildingIdPath = mock(Path.class);
        when(buildingPath.get("id")).thenReturn(buildingIdPath);
        Predicate equalPredicate = mock(Predicate.class);
        when(cb.equal(buildingIdPath, 1)).thenReturn(equalPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, 1, null, null, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(buildingIdPath, 1);
    }

    @Test
    void withFilter_shouldAddClassroomTypeIdPredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path typePath = mock(Path.class);
        when(root.get("classroomType")).thenReturn(typePath);
        Path typeIdPath = mock(Path.class);
        when(typePath.get("id")).thenReturn(typeIdPath);
        Predicate equalPredicate = mock(Predicate.class);
        when(cb.equal(typeIdPath, 2)).thenReturn(equalPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, 2, null, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(typeIdPath, 2);
    }

    @Test
    void withFilter_shouldAddCapacityMinPredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path capacityPath = mockPath("capacity");
        Predicate gePredicate = mock(Predicate.class);
        when(cb.greaterThanOrEqualTo(capacityPath, 20)).thenReturn(gePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, null, 20, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(capacityPath, 20);
    }

    @Test
    void withFilter_shouldAddCapacityMaxPredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path capacityPath = mockPath("capacity");
        Predicate lePredicate = mock(Predicate.class);
        when(cb.lessThanOrEqualTo(capacityPath, 50)).thenReturn(lePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, null, null, 50, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(capacityPath, 50);
    }

    @Test
    void withFilter_shouldAddFloorPredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path floorPath = mockPath("floor");
        Predicate equalPredicate = mock(Predicate.class);
        when(cb.equal(floorPath, 3)).thenReturn(equalPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, null, null, null, 3, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(floorPath, 3);
    }

    @Test
    void withFilter_shouldAddAvailablePredicate() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path availablePath = mockPath("available");
        Predicate equalPredicate = mock(Predicate.class);
        when(cb.equal(availablePath, true)).thenReturn(equalPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, null, null, null, null, true);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(availablePath, true);
    }

    @Test
    void withFilter_shouldCombineWithAnd() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path roomNumberPath = mockPath("roomNumber");
        Predicate likePredicate = mock(Predicate.class);
        when(cb.lower(roomNumberPath)).thenReturn(roomNumberPath);
        when(cb.like(roomNumberPath, "%101%")).thenReturn(likePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter("101", null, null, null, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);

        spec.toPredicate(root, query, cb);

        verify(cb).and(predicatesCaptor.capture());
        Predicate[] predicates = predicatesCaptor.getValue();
        assertTrue(predicates.length >= 2);
    }

    @Test
    void withFilter_shouldIgnoreBlankRoomNumber() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter("   ", null, null, null, null, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).and(predicatesCaptor.capture());
        Predicate[] predicates = predicatesCaptor.getValue();
        assertEquals(1, predicates.length);
    }

    @Test
    void withFilter_shouldFilterByCapacityExactRange() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path capacityPath = mockPath("capacity");
        Predicate gePredicate = mock(Predicate.class);
        Predicate lePredicate = mock(Predicate.class);
        when(cb.greaterThanOrEqualTo(capacityPath, 30)).thenReturn(gePredicate);
        when(cb.lessThanOrEqualTo(capacityPath, 30)).thenReturn(lePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        var filter = new ClassroomFilter(null, null, null, 30, 30, null, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).and(predicatesCaptor.capture());
        Predicate[] predicates = predicatesCaptor.getValue();
        assertTrue(predicates.length >= 3);
    }

    @Test
    void withFilter_shouldCombineThreeOrMorePredicates() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path roomNumberPath = mockPath("roomNumber");
        Predicate likePredicate = mock(Predicate.class);
        when(cb.lower(roomNumberPath)).thenReturn(roomNumberPath);
        when(cb.like(roomNumberPath, "%101%")).thenReturn(likePredicate);

        Path buildingPath = mock(Path.class);
        when(root.get("building")).thenReturn(buildingPath);
        Path buildingIdPath = mock(Path.class);
        when(buildingPath.get("id")).thenReturn(buildingIdPath);
        Predicate buildingPredicate = mock(Predicate.class);
        when(cb.equal(buildingIdPath, 1)).thenReturn(buildingPredicate);

        Path floorPath = mockPath("floor");
        Predicate floorPredicate = mock(Predicate.class);
        when(cb.equal(floorPath, 2)).thenReturn(floorPredicate);

        Path capacityPath = mockPath("capacity");
        Predicate gePredicate = mock(Predicate.class);
        when(cb.greaterThanOrEqualTo(capacityPath, 20)).thenReturn(gePredicate);

        Predicate andPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        var filter = new ClassroomFilter("101", 1, null, 20, null, 2, null);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).and(predicatesCaptor.capture());
        Predicate[] predicates = predicatesCaptor.getValue();
        assertTrue(predicates.length >= 4);
    }

    @Test
    void withFilter_shouldHandleAllFiltersSimultaneously() {
        Path deletedPath = mockPath("deleted");
        Predicate deletedPredicate = mock(Predicate.class);
        when(cb.isFalse(deletedPath)).thenReturn(deletedPredicate);

        Path roomNumberPath = mockPath("roomNumber");
        Predicate likePredicate = mock(Predicate.class);
        when(cb.lower(roomNumberPath)).thenReturn(roomNumberPath);
        when(cb.like(roomNumberPath, "%101%")).thenReturn(likePredicate);

        Path buildingPath = mock(Path.class);
        when(root.get("building")).thenReturn(buildingPath);
        Path buildingIdPath = mock(Path.class);
        when(buildingPath.get("id")).thenReturn(buildingIdPath);
        Predicate buildingPredicate = mock(Predicate.class);
        when(cb.equal(buildingIdPath, 1)).thenReturn(buildingPredicate);

        Path typePath = mock(Path.class);
        when(root.get("classroomType")).thenReturn(typePath);
        Path typeIdPath = mock(Path.class);
        when(typePath.get("id")).thenReturn(typeIdPath);
        Predicate typePredicate = mock(Predicate.class);
        when(cb.equal(typeIdPath, 2)).thenReturn(typePredicate);

        Path capacityPath = mockPath("capacity");
        Predicate gePredicate = mock(Predicate.class);
        when(cb.greaterThanOrEqualTo(capacityPath, 20)).thenReturn(gePredicate);

        Predicate lePredicate = mock(Predicate.class);
        when(cb.lessThanOrEqualTo(capacityPath, 50)).thenReturn(lePredicate);

        Path floorPath = mockPath("floor");
        Predicate floorPredicate = mock(Predicate.class);
        when(cb.equal(floorPath, 2)).thenReturn(floorPredicate);

        Path availablePath = mockPath("available");
        Predicate availablePredicate = mock(Predicate.class);
        when(cb.equal(availablePath, true)).thenReturn(availablePredicate);

        Predicate andPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        var filter = new ClassroomFilter("101", 1, 2, 20, 50, 2, true);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).and(predicatesCaptor.capture());
        Predicate[] predicates = predicatesCaptor.getValue();
        assertEquals(8, predicates.length);
    }
}
