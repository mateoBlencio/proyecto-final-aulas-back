package ar.edu.utn.frc.siga.sysacad.api;

import java.util.List;

public interface SysacadCatalogReader {

    List<SysacadBuildingDto> findBuildings();

    List<SysacadClassroomDto> findClassrooms();

    List<SysacadSpecialtyDto> findSpecialties();

    List<SysacadCommissionDto> findCommissions();
}
