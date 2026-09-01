package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomPermissionRepository extends SoftDeletableRepository<ClassroomPermission, Long> {

    List<ClassroomPermission> findByClassroomId(Long classroomId);

    List<ClassroomPermission> findByClassroomIdAndDeletedAtIsNull(Long classroomId);

    List<ClassroomPermission> findByClassroomIdInAndDeletedAtIsNull(Collection<Long> classroomIds);
}
