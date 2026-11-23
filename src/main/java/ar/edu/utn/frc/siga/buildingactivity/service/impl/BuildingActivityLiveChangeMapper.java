package ar.edu.utn.frc.siga.buildingactivity.service.impl;

import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * La actividad se registra en un listener asíncrono, después del commit del movimiento: avisar aparte evita que el
 * front pida la lista antes de que la fila exista.
 */
@Component
class BuildingActivityLiveChangeMapper implements LiveChangeMapper<BuildingActivity> {

    @Override
    public Class<BuildingActivity> entityType() {
        return BuildingActivity.class;
    }

    @Override
    public LiveChange map(BuildingActivity current, PreviousState previous) {
        return LiveChange.inBuildings(LiveTopic.BUILDING_ACTIVITY, Set.of(current.getBuildingId()));
    }
}
