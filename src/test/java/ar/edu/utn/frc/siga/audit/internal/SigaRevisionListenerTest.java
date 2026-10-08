package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.SigaRevision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SigaRevisionListener")
class SigaRevisionListenerTest {

    private final SigaRevisionListener listener = new SigaRevisionListener();

    @AfterEach
    void clearHolders() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        while (AuditOperationContext.current() != null) {
            AuditOperationContext.end();
        }
    }

    private SigaRevision newRevision() {
        SigaRevision revision = new SigaRevision();
        listener.newRevision(revision);
        return revision;
    }

    private static void bindHttpRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private static void authenticateAs(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    @Test
    @DisplayName("usuario autenticado dentro de un request: HUMAN con su nombre")
    void authenticatedUserInRequest_isHumanWithName() {
        bindHttpRequest();
        authenticateAs("docente@frc.utn.edu.ar");

        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.HUMAN);
        assertThat(revision.getUsuario()).isEqualTo("docente@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("usuario autenticado sin request en el hilo: sigue siendo HUMAN con su nombre")
    void authenticatedUserWithoutRequest_isHumanWithName() {
        authenticateAs("docente@frc.utn.edu.ar");

        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.HUMAN);
        assertThat(revision.getUsuario()).isEqualTo("docente@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("autenticación anónima dentro de un request (formulario público): HUMAN con usuario null")
    void anonymousInRequest_isHumanWithoutUser() {
        bindHttpRequest();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.HUMAN);
        assertThat(revision.getUsuario()).isNull();
    }

    @Test
    @DisplayName("sin autenticación pero con request en el hilo: HUMAN con usuario null")
    void noAuthenticationInRequest_isHumanWithoutUser() {
        bindHttpRequest();

        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.HUMAN);
        assertThat(revision.getUsuario()).isNull();
    }

    @Test
    @DisplayName("sin request ni autenticación (scheduler, listener asíncrono): SYSTEM con usuario null")
    void noRequestNoAuthentication_isSystem() {
        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(revision.getUsuario()).isNull();
    }

    @Test
    @DisplayName("autenticación anónima sin request en el hilo: SYSTEM")
    void anonymousWithoutRequest_isSystem() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(revision.getUsuario()).isNull();
    }

    @Test
    @DisplayName("una operación abierta no cambia la clasificación del actor y se copia a la revisión")
    void operationDoesNotAffectActorType() {
        AuditOperationContext.begin("Vencimiento automático");

        SigaRevision revision = newRevision();

        assertThat(revision.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(revision.getDescripcion()).isEqualTo("Vencimiento automático");
        assertThat(revision.getOperacionId()).isNotBlank();
    }
}
