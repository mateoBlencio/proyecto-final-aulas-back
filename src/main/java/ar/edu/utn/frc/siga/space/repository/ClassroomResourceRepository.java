package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomResourceRepository extends SoftDeletableRepository<ClassroomResource, Long> {

    List<ClassroomResource> findByClassroomId(Long classroomId);

    List<ClassroomResource> findByClassroomIdAndDeletedAtIsNull(Long classroomId);

    @EntityGraph(attributePaths = "resourceType")
    List<ClassroomResource> findByClassroomIdInAndDeletedAtIsNull(Collection<Long> classroomIds);

    @Query("select cr.classroom.id from ClassroomResource cr "
            + "where upper(cr.resourceType.name) = upper(:resourceTypeName) "
            + "and cr.quantity >= :minQuantity "
            + "and cr.deletedAt is null and cr.resourceType.deletedAt is null")
    Set<Long> findClassroomIdsWithResourceAtLeast(@Param("resourceTypeName") String resourceTypeName,
                                                   @Param("minQuantity") int minQuantity);
}
