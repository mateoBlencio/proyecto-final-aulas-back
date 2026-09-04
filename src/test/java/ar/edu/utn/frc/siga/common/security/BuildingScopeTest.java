package ar.edu.utn.frc.siga.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BuildingScope")
class BuildingScopeTest {

    @Test
    @DisplayName("unrestricted: allows cualquier edificio")
    void unrestrictedPermiteCualquierEdificio() {
        BuildingScope scope = BuildingScope.unrestricted();

        assertThat(scope.isUnrestricted()).isTrue();
        assertThat(scope.allows(1L)).isTrue();
        assertThat(scope.allows(999L)).isTrue();
    }

    @Test
    @DisplayName("denied: no permite ningún edificio")
    void deniedNoPermiteNingunEdificio() {
        BuildingScope scope = BuildingScope.denied();

        assertThat(scope.isUnrestricted()).isFalse();
        assertThat(scope.allows(1L)).isFalse();
        assertThat(scope.buildingIds()).isEmpty();
    }

    @Test
    @DisplayName("of: permite solo los edificios del conjunto")
    void ofPermiteSoloLosEdificiosDelConjunto() {
        BuildingScope scope = BuildingScope.of(Set.of(5L, 9L));

        assertThat(scope.allows(5L)).isTrue();
        assertThat(scope.allows(9L)).isTrue();
        assertThat(scope.allows(7L)).isFalse();
        assertThat(scope.buildingIds()).containsExactlyInAnyOrder(5L, 9L);
    }

    @Test
    @DisplayName("of: conjunto vacío degrada a denied")
    void ofConConjuntoVacioEsDenied() {
        BuildingScope scope = BuildingScope.of(Set.of());

        assertThat(scope.isUnrestricted()).isFalse();
        assertThat(scope.allows(1L)).isFalse();
    }

    @Test
    @DisplayName("union: irrestricto absorbe, gana sobre cualquier conjunto")
    void unionIrrestrictoAbsorbe() {
        BuildingScope unrestricted = BuildingScope.unrestricted();
        BuildingScope building5 = BuildingScope.of(Set.of(5L));

        assertThat(unrestricted.union(building5).isUnrestricted()).isTrue();
        assertThat(building5.union(unrestricted).isUnrestricted()).isTrue();
    }

    @Test
    @DisplayName("union: dos conjuntos se unen (caso Ana con dos edificios)")
    void unionDeDosConjuntosSeUnen() {
        BuildingScope building5 = BuildingScope.of(Set.of(5L));
        BuildingScope building9 = BuildingScope.of(Set.of(9L));

        BuildingScope union = building5.union(building9);

        assertThat(union.isUnrestricted()).isFalse();
        assertThat(union.buildingIds()).containsExactlyInAnyOrder(5L, 9L);
        assertThat(union.allows(5L)).isTrue();
        assertThat(union.allows(9L)).isTrue();
        assertThat(union.allows(7L)).isFalse();
    }

    @Test
    @DisplayName("union: denied más un conjunto da ese conjunto")
    void unionDeDeniedConAlgoDaEseAlgo() {
        BuildingScope denied = BuildingScope.denied();
        BuildingScope building5 = BuildingScope.of(Set.of(5L));

        assertThat(denied.union(building5)).isEqualTo(building5);
        assertThat(building5.union(denied)).isEqualTo(building5);
    }

    @Test
    @DisplayName("equals/hashCode: dos scopes con el mismo conjunto son iguales")
    void equalsPorContenido() {
        BuildingScope a = BuildingScope.of(Set.of(5L, 9L));
        BuildingScope b = BuildingScope.of(Set.of(9L, 5L));

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}
