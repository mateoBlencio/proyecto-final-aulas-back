package ar.edu.utn.frc.siga.common.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
