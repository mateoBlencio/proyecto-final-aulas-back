package ar.edu.utn.frc.siga.sysacad.api;

import java.util.List;

public interface SysacadSyncStateService {

    void recordSuccess(SysacadView view, int rowsAffected);

    void recordFailure(SysacadView view, String errorMessage);

    List<SysacadSyncStateDto> findAll();
}
