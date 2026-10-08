package ar.edu.utn.frc.siga.audit.internal;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * Test support for services that call {@code AuditOperations.describe}. The audit context is
 * package-private, so tests of other modules use this probe to run a service inside the real
 * aspect and read the description it ends up with.
 *
 * <p>Two ways to observe it:
 * <ul>
 *   <li>{@link #peek()} inside a mock answer: the description at that point of the call (for
 *       services that describe before writing).</li>
 *   <li>{@link #stamp()} inside a mock answer, simulating the Envers listener: a later
 *       {@code describe} then makes the operation dirty and {@link #rewrittenDescription()}
 *       returns what the updater received on close (for services that describe after writing).</li>
 * </ul>
 */
public final class AuditDescriptionProbe {

    private final RevisionDescriptionUpdater updater = Mockito.mock(RevisionDescriptionUpdater.class);
    private final AtomicReference<String> peeked = new AtomicReference<>();

    /** Wraps {@code target} (class-based proxy) with the real audit aspect. */
    public <T> T audited(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditOperationAspect(updater));
        return factory.getProxy();
    }

    /** Simulates the first revision of the operation being stamped. */
    public static void stamp() {
        AuditOperationContext.markStamped();
    }

    /** Records the description of the operation in progress; returns null outside one. */
    public String peek() {
        AuditOperationContext.Operation operation = AuditOperationContext.current();
        String description = operation == null ? null : operation.description();
        peeked.set(description);
        return description;
    }

    public String peeked() {
        return peeked.get();
    }

    /** Last description the updater received, or null if the aspect never asked for a rewrite. */
    public String rewrittenDescription() {
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(updater, atLeast(0)).update(Mockito.any(), description.capture());
        List<String> all = description.getAllValues();
        return all.isEmpty() ? null : all.getLast();
    }

    public void verifyNoRewrite() {
        Mockito.verifyNoInteractions(updater);
    }
}
