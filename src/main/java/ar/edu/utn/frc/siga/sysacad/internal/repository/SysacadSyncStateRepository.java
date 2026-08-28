package ar.edu.utn.frc.siga.sysacad.internal.repository;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSyncState;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysacadSyncStateRepository extends JpaRepository<SysacadSyncState, Long> {

    Optional<SysacadSyncState> findByView(SysacadView view);
}
