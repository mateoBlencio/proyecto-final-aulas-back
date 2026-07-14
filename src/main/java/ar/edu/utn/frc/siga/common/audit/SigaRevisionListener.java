package ar.edu.utn.frc.siga.common.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Completa el "quién" de cada revisión de Envers con el usuario autenticado del request que
 * disparó la transacción.
 *
 * <p>Envers lo instancia con su constructor no-arg —no es un bean de Spring— y lo invoca al crear
 * cada fila nueva de {@code revinfo}. No depende del módulo {@code auth} ni de {@code SecurityUser}:
 * {@link Authentication#getName()} ya devuelve el email (subject del JWT), y
 * {@code SecurityContextHolder} es de {@code spring-security-core}, no de un módulo de negocio.
 */
public class SigaRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        SigaRevision revision = (SigaRevision) revisionEntity;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean sinUsuarioAutenticado = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;

        revision.setUsuario(sinUsuarioAutenticado ? null : authentication.getName());
    }
}
