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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // ---------- allowedActions ----------

    @Test
    @DisplayName("allowedActions: subsecretaría en NEW devuelve exactamente [ASSIGN, DERIVE, CANCEL]")
    void allowedActionsSubsecretariaEnNew() {
        asSubsecretaria();
        RoomRequestAccessControl.Actor actor = accessControl.resolveActor(SUBSECRETARIA_EMAIL);

        assertThat(accessControl.allowedActions(item(RoomRequestStatus.NEW, null), actor))
                .containsExactly(Action.ASSIGN, Action.DERIVE, Action.CANCEL);
    }

    @Test
    @DisplayName("allowedActions: subsecretaría en IN_EVALUATION con edificio derivado devuelve solo [CANCEL]")
    void allowedActionsSubsecretariaEnEvaluacionConEdificio() {
        asSubsecretaria();
        RoomRequestAccessControl.Actor actor = accessControl.resolveActor(SUBSECRETARIA_EMAIL);

        assertThat(accessControl.allowedActions(item(RoomRequestStatus.IN_EVALUATION, SU_BUILDING), actor))
                .containsExactly(Action.CANCEL);
    }

    @Test
    @DisplayName("allowedActions: auxiliar del edificio 1 en DERIVED_TO_BUILDING: [ASSIGN, RETURN, CANCEL] si es el 1, vacío si es el 2")
    void allowedActionsAuxiliarSegunEdificio() {
        asAuxiliar(SU_BUILDING);
        RoomRequestAccessControl.Actor actor = accessControl.resolveActor(AUXILIAR_EMAIL);

        assertThat(accessControl.allowedActions(item(RoomRequestStatus.DERIVED_TO_BUILDING, SU_BUILDING), actor))
                .containsExactly(Action.ASSIGN, Action.RETURN, Action.CANCEL);
        assertThat(accessControl.allowedActions(item(RoomRequestStatus.DERIVED_TO_BUILDING, OTRO_BUILDING), actor))
                .isEmpty();
    }

    @Test
    @DisplayName("allowedActions: un rol sin SUBSECRETARIA ni AUXILIAR_AULICO (CONSULTA) no puede nada")
    void allowedActionsOtroRolVacio() {
        String consultaEmail = "consulta@frc.utn.edu.ar";
        asOtroRol(consultaEmail);
        RoomRequestAccessControl.Actor actor = accessControl.resolveActor(consultaEmail);

        assertThat(accessControl.allowedActions(item(RoomRequestStatus.IN_EVALUATION, SU_BUILDING), actor)).isEmpty();
    }

    @Test
    @DisplayName("allowedActions y authorize coinciden para cada estado, acción, perfil y edificio derivado")
    void allowedActionsCoincideConAuthorize() {
        String consultaEmail = "consulta@frc.utn.edu.ar";
        Map<String, Runnable> perfiles = new LinkedHashMap<>();
        perfiles.put(SUBSECRETARIA_EMAIL, this::asSubsecretaria);
        perfiles.put(AUXILIAR_EMAIL + "#edificio1", () -> asAuxiliar(SU_BUILDING));
        perfiles.put(AUXILIAR_EMAIL + "#global", this::asAuxiliarGlobal);
        perfiles.put(consultaEmail, () -> asOtroRol(consultaEmail));

        int combinaciones = 0;
        for (Map.Entry<String, Runnable> perfil : perfiles.entrySet()) {
            perfil.getValue().run();
            String email = perfil.getKey().startsWith(AUXILIAR_EMAIL) ? AUXILIAR_EMAIL : perfil.getKey();
            RoomRequestAccessControl.Actor actor = accessControl.resolveActor(email);
            for (RoomRequestStatus status : RoomRequestStatus.values()) {
                for (Long derivedBuildingId : Arrays.asList(null, SU_BUILDING, OTRO_BUILDING)) {
                    RoomRequestItem item = item(status, derivedBuildingId);
                    List<Action> allowed = accessControl.allowedActions(item, actor);
                    for (Action action : Action.values()) {
                        boolean authorizes = authorizes(item, email, action);
                        assertThat(allowed.contains(action))
                                .as("perfil=%s estado=%s edificio=%s acción=%s",
                                        perfil.getKey(), status, derivedBuildingId, action)
                                .isEqualTo(authorizes);
                        combinaciones++;
                    }
                }
            }
        }
        assertThat(combinaciones).isEqualTo(4 * RoomRequestStatus.values().length * 3 * Action.values().length);
    }

    private boolean authorizes(RoomRequestItem item, String email, Action action) {
        try {
            accessControl.authorize(item, email, action);
            return true;
        } catch (RoomRequestForbiddenException e) {
            return false;
        }
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
