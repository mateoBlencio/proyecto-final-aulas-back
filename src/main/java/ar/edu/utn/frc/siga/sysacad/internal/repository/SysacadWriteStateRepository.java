package ar.edu.utn.frc.siga.sysacad.internal.repository;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysacadWriteStateRepository extends JpaRepository<SysacadWriteState, Short> {
}
