package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.AuditCause;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditOperationAspect")
class AuditOperationAspectTest {

    @AfterEach
    void clear() {
        while (AuditOperationContext.current() != null) {
            AuditOperationContext.end();
        }
    }

    private record Cause(String originOperationId) implements AuditCause {
    }

    /** Captures the operation that is open while the audited method runs. */
    public static class Target {

        AuditOperationContext.Operation seen;

        @AuditOperation("Operación hija")
        public void handle(AuditCause cause) {
            seen = AuditOperationContext.current();
        }

        @AuditOperation("Operación con dos causas")
        public void handleTwo(AuditCause first, AuditCause second) {
            seen = AuditOperationContext.current();
        }

        @AuditOperation("Operación con argumentos mezclados")
        public void handleMixed(String name, Long count, AuditCause cause, Object extra) {
            seen = AuditOperationContext.current();
        }

        @AuditOperation("Operación sin causa")
        public void handlePlain(String notACause) {
            seen = AuditOperationContext.current();
        }

        @AuditOperation("Operación que falla")
        public void fail(AuditCause cause) {
            seen = AuditOperationContext.current();
            throw new IllegalStateException("boom");
        }
    }

    private Target proxied(Target target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new AuditOperationAspect());
        return factory.getProxy();
    }

    @Test
    @DisplayName("un argumento AuditCause con id abre la operación con ese padre")
    void causeWithIdOpensOperationWithParent() {
        Target target = new Target();

        proxied(target).handle(new Cause("parent-op-1"));

        assertThat(target.seen).isNotNull();
        assertThat(target.seen.parentId()).isEqualTo("parent-op-1");
        assertThat(target.seen.description()).isEqualTo("Operación hija");
        assertThat(target.seen.id()).isNotEqualTo("parent-op-1");
    }

    @Test
    @DisplayName("un AuditCause con id null abre la operación sin padre")
    void causeWithNullIdOpensOperationWithoutParent() {
        Target target = new Target();

        proxied(target).handle(new Cause(null));

        assertThat(target.seen).isNotNull();
        assertThat(target.seen.parentId()).isNull();
    }

    @Test
    @DisplayName("un argumento que no es AuditCause no aporta padre")
    void nonCauseArgumentHasNoParent() {
        Target target = new Target();

        proxied(target).handlePlain("parent-op-1");

        assertThat(target.seen.parentId()).isNull();
    }

    @Test
    @DisplayName("con varios AuditCause toma el primero cuyo id no es null")
    void takesFirstCauseWithNonNullId() {
        Target target = new Target();

        proxied(target).handleTwo(new Cause(null), new Cause("parent-op-2"));

        assertThat(target.seen.parentId()).isEqualTo("parent-op-2");
    }

    @Test
    @DisplayName("encuentra el AuditCause entre varios argumentos de otros tipos")
    void findsCauseAmongOtherArguments() {
        Target target = new Target();

        proxied(target).handleMixed("x", 5L, new Cause("parent-op-3"), null);

        assertThat(target.seen.parentId()).isEqualTo("parent-op-3");
    }

    @Test
    @DisplayName("cierra la operación aunque el método falle")
    void closesOperationWhenMethodThrows() {
        Target target = new Target();

        assertThatThrownBy(() -> proxied(target).fail(new Cause("parent-op-1")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(target.seen.parentId()).isEqualTo("parent-op-1");
        assertThat(AuditOperationContext.current()).isNull();
    }
}
