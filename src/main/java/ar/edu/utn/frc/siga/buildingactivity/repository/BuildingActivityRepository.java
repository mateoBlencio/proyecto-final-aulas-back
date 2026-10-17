package ar.edu.utn.frc.siga.buildingactivity.repository;

import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingActivityRepository
        extends JpaRepository<BuildingActivity, Long>, JpaSpecificationExecutor<BuildingActivity> {

    boolean existsByOperationId(String operationId);
}
