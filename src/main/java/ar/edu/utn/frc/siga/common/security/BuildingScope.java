package ar.edu.utn.frc.siga.common.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class BuildingScope {

    private static final BuildingScope UNRESTRICTED = new BuildingScope(true, Set.of());
    private static final BuildingScope DENIED = new BuildingScope(false, Set.of());

    private final boolean unrestricted;
    private final Set<Long> buildingIds;

    private BuildingScope(boolean unrestricted, Set<Long> buildingIds) {
        this.unrestricted = unrestricted;
        this.buildingIds = Set.copyOf(buildingIds);
    }

    public static BuildingScope unrestricted() {
        return UNRESTRICTED;
    }

    public static BuildingScope denied() {
        return DENIED;
    }

    public static BuildingScope of(Set<Long> buildingIds) {
        if (buildingIds.isEmpty()) {
            return DENIED;
        }
        return new BuildingScope(false, buildingIds);
    }

    public boolean isUnrestricted() {
        return unrestricted;
    }

    /** Vacío si el alcance es irrestricto: no hay un conjunto finito de edificios que enumerar. */
    public Set<Long> buildingIds() {
        return buildingIds;
    }

    public boolean allows(Long buildingId) {
        return unrestricted || buildingIds.contains(buildingId);
    }

    /** Alcance combinado: si alguno de los dos es irrestricto, gana el irrestricto; si no, se unen los conjuntos. */
    public BuildingScope union(BuildingScope other) {
        if (this.unrestricted || other.unrestricted) {
            return UNRESTRICTED;
        }
        if (this.buildingIds.isEmpty()) {
            return other;
        }
        if (other.buildingIds.isEmpty()) {
            return this;
        }
        Set<Long> merged = new HashSet<>(this.buildingIds);
        merged.addAll(other.buildingIds);
        return new BuildingScope(false, merged);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildingScope that)) {
            return false;
        }
        return unrestricted == that.unrestricted && buildingIds.equals(that.buildingIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unrestricted, buildingIds);
    }

    @Override
    public String toString() {
        return unrestricted ? "BuildingScope[UNRESTRICTED]" : "BuildingScope" + buildingIds;
    }
}
