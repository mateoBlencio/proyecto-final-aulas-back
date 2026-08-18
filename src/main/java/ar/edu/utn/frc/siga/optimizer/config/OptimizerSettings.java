package ar.edu.utn.frc.siga.optimizer.config;

public interface OptimizerSettings {

    long getUnimprovedSecondsLimit();

    long getSolverSecondsSpentLimit();

    int getOvercrowdingWeight();

    int getSameCommissionDiffRoomWeight();

    int getSameCommissionDiffBuildingWeight();

    int getUnusedCapacityWeight();
}
