package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadCatalogReaderImpl implements SysacadCatalogReader {

    private final BuildingViewFetcher buildingViewFetcher;
    private final ClassroomViewFetcher classroomViewFetcher;
    private final SpecialtyViewFetcher specialtyViewFetcher;
    private final CommissionViewFetcher commissionViewFetcher;
    private final SysacadCatalogMapper mapper;

    @Override
    public List<SysacadBuildingDto> findBuildings() {
        return buildingViewFetcher.fetch().stream().map(mapper::toBuilding).toList();
    }

    @Override
    public List<SysacadClassroomDto> findClassrooms() {
        return classroomViewFetcher.fetch().stream().map(mapper::toClassroom).toList();
    }

    @Override
    public List<SysacadSpecialtyDto> findSpecialties() {
        return specialtyViewFetcher.fetch().stream().map(mapper::toSpecialty).toList();
    }

    @Override
    public List<SysacadCommissionDto> findCommissions() {
        return commissionViewFetcher.fetch().stream().map(mapper::toCommission).toList();
    }
}
