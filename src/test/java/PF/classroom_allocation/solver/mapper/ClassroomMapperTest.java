package PF.classroom_allocation.solver.mapper;

import PF.classroom_allocation.solver.dto.request.AllocationParametersDto;
import PF.classroom_allocation.solver.dto.request.ClassroomRequestDto;
import PF.classroom_allocation.solver.model.Classroom;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassroomMapperTest {

    private final ClassroomMapper mapper = new ClassroomMapper();

    private ClassroomRequestDto dto(String id, String building) {
        ClassroomRequestDto dto = new ClassroomRequestDto();
        dto.setId(id); dto.setName("Room " + id);
        dto.setBuilding(building); dto.setCapacityM2(80f);
        return dto;
    }

    private List<ClassroomRequestDto> allRooms() {
        return List.of(
                dto("aula-513", "Edif. Dr. Gallardo"),
                dto("aula-524", "Edif. Dr. Gallardo"),
                dto("aula-220", "Edif.Central"),
                dto("aula-415", "Edif. Ing.Inchaurrondo")
        );
    }

    @Test
    void upCm001_noExclusions_allClassrooms() {
        List<Classroom> result = mapper.toClassrooms(allRooms(), null);
        assertThat(result).hasSize(4);
    }

    @Test
    void upCm002_excludeById() {
        AllocationParametersDto params = new AllocationParametersDto();
        params.setExcludedClassroomIds(List.of("aula-513"));
        List<Classroom> result = mapper.toClassrooms(allRooms(), params);
        assertThat(result).hasSize(3);
        assertThat(result).noneMatch(c -> c.getId().equals("aula-513"));
    }

    @Test
    void upCm003_excludeByBuilding() {
        AllocationParametersDto params = new AllocationParametersDto();
        params.setExcludedBuildingNames(List.of("Edif. Dr. Gallardo"));
        List<Classroom> result = mapper.toClassrooms(allRooms(), params);
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(c -> c.getId().startsWith("aula-5"));
    }

    @Test
    void upCm004_excludeIdAndBuilding_union() {
        AllocationParametersDto params = new AllocationParametersDto();
        params.setExcludedClassroomIds(List.of("aula-220"));
        params.setExcludedBuildingNames(List.of("Edif. Dr. Gallardo"));
        List<Classroom> result = mapper.toClassrooms(allRooms(), params);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("aula-415");
    }

    @Test
    void upCm005_excludeNonExistentId_noError() {
        AllocationParametersDto params = new AllocationParametersDto();
        params.setExcludedClassroomIds(List.of("aula-999"));
        List<Classroom> result = mapper.toClassrooms(allRooms(), params);
        assertThat(result).hasSize(4);
    }
}
