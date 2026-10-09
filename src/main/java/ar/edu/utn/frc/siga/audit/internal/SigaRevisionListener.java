package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.SigaRevision;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

public class SigaRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        SigaRevision revision = (SigaRevision) revisionEntity;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean unauthenticated = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;

        revision.setUsuario(unauthenticated ? null : authentication.getName());
        // Without a session, a write inside an HTTP request comes from a public form (a person);
        // without a request either, it comes from a scheduler or an async listener.
        boolean system = unauthenticated && RequestContextHolder.getRequestAttributes() == null;
        revision.setActorType(system ? ActorType.SYSTEM : ActorType.HUMAN);

        AuditOperationContext.Operation operation = AuditOperationContext.current();
        if (operation != null) {
            revision.setOperacionId(operation.id());
            revision.setDescripcion(operation.description());
        }
    }
}
