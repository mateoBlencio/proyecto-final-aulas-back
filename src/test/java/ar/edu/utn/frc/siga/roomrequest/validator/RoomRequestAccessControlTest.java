package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestForbiddenException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestAccessControl")
class RoomRequestAccessControlTest {

    private static final String SUBSECRETARIA_EMAIL = "subsecretaria@frc.utn.edu.ar";
    private static final String AUXILIAR_EMAIL = "auxiliar@frc.utn.edu.ar";
    private static final Long SU_BUILDING = 1L;
    private static final Long OTRO_BUILDING = 2L;

    @Mock
    private UserService userService;

    private RoomRequestAccessControl accessControl;

    private void asSubsecretaria() {
        lenient().when(userService.hasRole(SUBSECRETARIA_EMAIL, SystemRole.SUBSECRETARIA)).thenReturn(true);
        accessControl = new RoomRequestAccessControl(userService);
    }

    private void asAuxiliar(Long... buildingIds) {
        lenient().when(userService.hasRole(AUXILIAR_EMAIL, SystemRole.SUBSECRETARIA)).thenReturn(false);
        lenient().when(userService.hasRole(AUXILIAR_EMAIL, SystemRole.AUXILIAR_AULICO)).thenReturn(true);
        lenient().when(userService.hasGlobalRole(AUXILIAR_EMAIL, SystemRole.AUXILIAR_AULICO)).thenReturn(false);
        lenient().when(userService.findBuildingIdsForRole(AUXILIAR_EMAIL, SystemRole.AUXILIAR_AULICO))
                .thenReturn(Set.of(buildingIds));
        accessControl = new RoomRequestAccessControl(userService);
    }

    private void asAuxiliarGlobal() {
        lenient().when(userService.hasRole(AUXILIAR_EMAIL, SystemRole.SUBSECRETARIA)).thenReturn(false);
        lenient().when(userService.hasRole(AUXILIAR_EMAIL, SystemRole.AUXILIAR_AULICO)).thenReturn(true);
        lenient().when(userService.hasGlobalRole(AUXILIAR_EMAIL, SystemRole.AUXILIAR_AULICO)).thenReturn(true);
        accessControl = new RoomRequestAccessControl(userService);
    }

    /** Un rol sin AUXILIAR_AULICO ni SUBSECRETARIA (p. ej. CONSULTA): no debería recortarse como auxiliar. */
    private void asOtroRol(String email) {
        lenient().when(userService.hasRole(email, SystemRole.SUBSECRETARIA)).thenReturn(false);
        lenient().when(userService.hasRole(email, SystemRole.AUXILIAR_AULICO)).thenReturn(false);
        accessControl = new RoomRequestAccessControl(userService);
    }

    private static RoomRequestItem item(RoomRequestStatus status, Long derivedBuildingId) {
        return RoomRequestItem.builder().id(1L).status(status).derivedBuildingId(derivedBuildingId).build();
    }

    // ---------- subsecretaría ----------

    @Test
    @DisplayName("subsecretaría en NEW: asigna, deriva y cancela; no notifica")
    void subsecretariaEnNew() {
        asSubsecretaria();
        RoomRequestItem item = item(RoomRequestStatus.NEW, null);

        assertAllowed(item, SUBSECRETARIA_EMAIL, Action.ASSIGN, Action.DERIVE, Action.CANCEL);
        assertForbidden(item, SUBSECRETARIA_EMAIL, Action.RETURN, Action.NOTIFY);
    }

    @Test
    @DisplayName("subsecretaría en DERIVED_TO_BUILDING: devuelve y cancela, pero no asigna")
    void subsecretariaEnDerivado() {
        asSubsecretaria();
        RoomRequestItem item = item(RoomRequestStatus.DERIVED_TO_BUILDING, SU_BUILDING);

        assertAllowed(item, SUBSECRETARIA_EMAIL, Action.RETURN, Action.CANCEL);
        assertForbidden(item, SUBSECRETARIA_EMAIL, Action.ASSIGN, Action.DERIVE, Action.NOTIFY);
    }

    @Test
    @DisplayName("subsecretaría en IN_EVALUATION: asigna y avisa sólo si el pedido nunca pasó por un edificio, "
            + "pero cancela siempre")
    void subsecretariaEnEvaluacion() {
        asSubsecretaria();
        RoomRequestItem sinEdificio = item(RoomRequestStatus.IN_EVALUATION, null);
        RoomRequestItem conEdificio = item(RoomRequestStatus.IN_EVALUATION, SU_BUILDING);

        assertAllowed(sinEdificio, SUBSECRETARIA_EMAIL, Action.ASSIGN, Action.NOTIFY, Action.CANCEL);
        assertForbidden(conEdificio, SUBSECRETARIA_EMAIL, Action.ASSIGN, Action.NOTIFY);
        assertAllowed(conEdificio, SUBSECRETARIA_EMAIL, Action.CANCEL);
    }

    @Test
    @DisplayName("subsecretaría en RESOLVED o CANCELLED: nada")
    void subsecretariaEnEstadoTerminal() {
        asSubsecretaria();
        for (RoomRequestStatus status : Set.of(RoomRequestStatus.RESOLVED, RoomRequestStatus.CANCELLED)) {
            assertForbidden(item(status, SU_BUILDING), SUBSECRETARIA_EMAIL, Action.values());
        }
    }

    // ---------- auxiliar áulico ----------

    @Test
    @DisplayName("auxiliar en NEW: nada, es exclusivo de subsecretaría hasta que derive")
    void auxiliarEnNew() {
        asAuxiliar(SU_BUILDING);
        assertForbidden(item(RoomRequestStatus.NEW, null), AUXILIAR_EMAIL, Action.values());
    }

    @Test
    @DisplayName("auxiliar en DERIVED_TO_BUILDING de su edificio: asigna, devuelve y cancela")
    void auxiliarEnDerivadoASuEdificio() {
        asAuxiliar(SU_BUILDING);
        RoomRequestItem item = item(RoomRequestStatus.DERIVED_TO_BUILDING, SU_BUILDING);

        assertAllowed(item, AUXILIAR_EMAIL, Action.ASSIGN, Action.RETURN, Action.CANCEL);
        assertForbidden(item, AUXILIAR_EMAIL, Action.DERIVE, Action.NOTIFY);
    }

    @Test
    @DisplayName("auxiliar en DERIVED_TO_BUILDING de OTRO edificio: nada, ni cancelar")
    void auxiliarEnDerivadoAOtroEdificio() {
        asAuxiliar(SU_BUILDING);
        RoomRequestItem item = item(RoomRequestStatus.DERIVED_TO_BUILDING, OTRO_BUILDING);

        assertForbidden(item, AUXILIAR_EMAIL, Action.values());
    }

    @Test
    @DisplayName("auxiliar en IN_EVALUATION de su edificio: asigna, avisa y cancela")
    void auxiliarEnEvaluacionDeSuEdificio() {
        asAuxiliar(SU_BUILDING);
        RoomRequestItem item = item(RoomRequestStatus.IN_EVALUATION, SU_BUILDING);

        assertAllowed(item, AUXILIAR_EMAIL, Action.ASSIGN, Action.NOTIFY, Action.CANCEL);
        assertForbidden(item, AUXILIAR_EMAIL, Action.DERIVE, Action.RETURN);
    }

    @Test
    @DisplayName("auxiliar con alcance GLOBAL: opera cualquier edificio derivado, como si fuera el dueño")
    void auxiliarConAlcanceGlobal() {
        asAuxiliarGlobal();
        RoomRequestItem item = item(RoomRequestStatus.DERIVED_TO_BUILDING, OTRO_BUILDING);

        assertAllowed(item, AUXILIAR_EMAIL, Action.ASSIGN, Action.RETURN, Action.CANCEL);
    }

    @Test
    @DisplayName("auxiliar en IN_EVALUATION de otro edificio: nada")
    void auxiliarEnEvaluacionDeOtroEdificio() {
        asAuxiliar(SU_BUILDING);
        RoomRequestItem item = item(RoomRequestStatus.IN_EVALUATION, OTRO_BUILDING);

        assertForbidden(item, AUXILIAR_EMAIL, Action.values());
    }

    @Test
    @DisplayName("auxiliar sin ningún edificio a cargo: en un pedido derivado, nada")
    void auxiliarSinEdificioEnDerivado() {
        asAuxiliar();
        assertForbidden(item(RoomRequestStatus.DERIVED_TO_BUILDING, SU_BUILDING), AUXILIAR_EMAIL, Action.values());
    }

    // ---------- readScope ----------

    @Test
    @DisplayName("readScope de subsecretaría: sin recorte")
    void readScopeSubsecretaria() {
        asSubsecretaria();
        assertThat(accessControl.readScope(SUBSECRETARIA_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("readScope de auxiliar: acotado a sus edificios")
    void readScopeAuxiliar() {
        asAuxiliar(SU_BUILDING, OTRO_BUILDING);
        assertThat(accessControl.readScope(AUXILIAR_EMAIL)).contains(Set.of(SU_BUILDING, OTRO_BUILDING));
    }

    @Test
    @DisplayName("readScope de auxiliar sin edificios: recorte vacío, no ve nada")
    void readScopeAuxiliarSinEdificios() {
        asAuxiliar();
        assertThat(accessControl.readScope(AUXILIAR_EMAIL)).contains(Set.of());
    }

    @Test
    @DisplayName("readScope de auxiliar con alcance GLOBAL: sin recorte, ve todo")
    void readScopeAuxiliarGlobal() {
        asAuxiliarGlobal();
        assertThat(accessControl.readScope(AUXILIAR_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("readScope de un rol sin AUXILIAR_AULICO ni SUBSECRETARIA (p. ej. CONSULTA): sin recorte")
    void readScopeOtroRol() {
        String consultaEmail = "consulta@frc.utn.edu.ar";
        asOtroRol(consultaEmail);
        assertThat(accessControl.readScope(consultaEmail)).isEmpty();
    }

    @Test
    @DisplayName("authorizeRead: subsecretaría lee cualquier pedido")
    void authorizeReadSubsecretaria() {
        asSubsecretaria();
        assertThatCode(() -> accessControl.authorizeRead(item(RoomRequestStatus.NEW, null), SUBSECRETARIA_EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("authorizeRead: auxiliar lee un pedido de su edificio, no el de otro ni uno sin derivar")
    void authorizeReadAuxiliar() {
        asAuxiliar(SU_BUILDING);

        assertThatCode(() -> accessControl.authorizeRead(
                item(RoomRequestStatus.DERIVED_TO_BUILDING, SU_BUILDING), AUXILIAR_EMAIL))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> accessControl.authorizeRead(
                item(RoomRequestStatus.DERIVED_TO_BUILDING, OTRO_BUILDING), AUXILIAR_EMAIL))
                .isInstanceOf(RoomRequestForbiddenException.class);
        assertThatThrownBy(() -> accessControl.authorizeRead(item(RoomRequestStatus.NEW, null), AUXILIAR_EMAIL))
                .isInstanceOf(RoomRequestForbiddenException.class);
    }

    private void assertAllowed(RoomRequestItem item, String email, Action... actions) {
        for (Action action : actions) {
            assertThatCode(() -> accessControl.authorize(item, email, action))
                    .as("%s en %s", action, item.getStatus())
                    .doesNotThrowAnyException();
        }
    }

    private void assertForbidden(RoomRequestItem item, String email, Action... actions) {
        for (Action action : actions) {
            assertThatThrownBy(() -> accessControl.authorize(item, email, action))
                    .as("%s en %s", action, item.getStatus())
                    .isInstanceOf(RoomRequestForbiddenException.class);
        }
    }
}
