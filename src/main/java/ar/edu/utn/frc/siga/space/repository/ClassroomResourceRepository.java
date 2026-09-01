package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomResourceRepository extends SoftDeletableRepository<ClassroomResource, Long> {

    List<ClassroomResource> findByClassroomId(Long classroomId);

    List<ClassroomResource> findByClassroomIdAndDeletedAtIsNull(Long classroomId);

    @EntityGraph(attributePaths = "resourceType")
    List<ClassroomResource> findByClassroomIdInAndDeletedAtIsNull(Collection<Long> classroomIds);
}
