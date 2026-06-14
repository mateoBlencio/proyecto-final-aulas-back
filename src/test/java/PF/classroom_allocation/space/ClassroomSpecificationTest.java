package PF.classroom_allocation.space;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.model.Classroom;
import PF.classroom_allocation.space.specification.ClassroomSpecification;
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
}
