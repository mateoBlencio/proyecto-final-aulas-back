package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceTypeRepository extends SoftDeletableRepository<ResourceType, Long> {

    Optional<ResourceType> findByCodeAndDeletedAtIsNull(String code);
}
