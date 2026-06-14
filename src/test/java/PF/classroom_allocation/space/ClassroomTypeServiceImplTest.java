package PF.classroom_allocation.space;

import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.model.ClassroomType;
import PF.classroom_allocation.space.repository.ClassroomTypeRepository;
import PF.classroom_allocation.space.service.impl.ClassroomTypeServiceImpl;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassroomTypeServiceImplTest {

    @Mock
    private ClassroomTypeRepository classroomTypeRepository;

    private ClassroomTypeServiceImpl classroomTypeService;

    private ClassroomType classroomType;

    @BeforeEach
    void setUp() {
        classroomTypeService = new ClassroomTypeServiceImpl(classroomTypeRepository);

        classroomType = new ClassroomType();
        classroomType.setId(1);
        classroomType.setDescription("CLASSROOM");
    }

    @Test
    void findById_shouldReturnTypeWhenExists() {
        when(classroomTypeRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(classroomType));

        ClassroomType result = classroomTypeService.findById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("CLASSROOM", result.getDescription());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(classroomTypeRepository.findByIdAndDeletedFalse(999)).thenReturn(Optional.empty());

        var ex = assertThrows(ResourceNotFoundException.class, () -> classroomTypeService.findById(999));
        assertTrue(ex.getMessage().contains("ClassroomType not found"));
    }
}
