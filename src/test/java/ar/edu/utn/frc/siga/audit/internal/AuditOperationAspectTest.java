package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.AuditCause;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AuditOperationAspect")
class AuditOperationAspectTest {

    private final RevisionDescriptionUpdater updater = mock(RevisionDescriptionUpdater.class);
    private final OperationSummary summary = mock(OperationSummary.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<OperationSummary> summaryProvider = mock(ObjectProvider.class);

    @BeforeEach
    void stubSummaryProvider() {
        when(summaryProvider.getObject()).thenReturn(summary);
    }

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

        @AuditOperation("Inicial")
        public void describeAfterStamp() {
            seen = AuditOperationContext.current();
            AuditOperationContext.markStamped();
            AuditOperations.describe("Final");
        }

        @AuditOperation("Inicial")
        public void describeBeforeStamp() {
            seen = AuditOperationContext.current();
            AuditOperations.describe("Final");
            AuditOperationContext.markStamped();
        }

        @AuditOperation("Inicial")
        public void stampWithoutDescribe() {
            AuditOperationContext.markStamped();
        }

        @AuditOperation("Inicial")
        public void describeAfterStampThenFail() {
            seen = AuditOperationContext.current();
            AuditOperationContext.markStamped();
            AuditOperations.describe("Final");
            throw new IllegalStateException("boom");
        }

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

        @AuditOperation(value = "Baja de materia", subject = "Subject")
        public void withSubject() {
            seen = AuditOperationContext.current();
            AuditOperationContext.markStamped();
        }

        @AuditOperation(value = "Baja de materia", subject = "Subject")
        public void withSubjectAndLateDescribe() {
            seen = AuditOperationContext.current();
            AuditOperationContext.markStamped();
            AuditOperations.describe("Final");
        }

        @AuditOperation(value = "Baja de materia", subject = "Subject")
        public void withSubjectThenFail() {
            seen = AuditOperationContext.current();
            throw new IllegalStateException("boom");
        }

        @AuditOperation("Operación que falla")
        public void fail(AuditCause cause) {
            seen = AuditOperationContext.current();
            throw new IllegalStateException("boom");
        }
    }

    private Target proxied(Target target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new AuditOperationAspect(updater, summaryProvider));
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

    @Test
    @DisplayName("describe tardío (después de sellar una revisión) reescribe la descripción de esa operación al cerrar")
    void lateDescribeCallsUpdaterWithOperationId() {
        Target target = new Target();

        proxied(target).describeAfterStamp();

        verify(updater).update(target.seen.id(), "Final");
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("describe antes de sellar no necesita UPDATE: el updater no se llama")
    void earlyDescribeDoesNotCallUpdater() {
        Target target = new Target();

        proxied(target).describeBeforeStamp();

        verifyNoInteractions(updater);
    }

    @Test
    @DisplayName("revisión sellada sin describe no llama al updater")
    void stampWithoutDescribeDoesNotCallUpdater() {
        proxied(new Target()).stampWithoutDescribe();

        verifyNoInteractions(updater);
    }

    @Test
    @DisplayName("si el método falla después de un describe tardío reescribe igual la descripción de lo ya commiteado y propaga la excepción")
    void failingMethodAfterLateDescribeStillCallsUpdater() {
        Target target = new Target();

        assertThatThrownBy(() -> proxied(target).describeAfterStampThenFail())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(updater).update(target.seen.id(), "Final");
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("si el método falla sin haber descrito nada el updater no se llama")
    void failingMethodWithoutDescribeDoesNotCallUpdater() {
        assertThatThrownBy(() -> proxied(new Target()).fail(new Cause("parent-op-1")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(updater);
    }

    @Test
    @DisplayName("con subject arma el resumen con el id de la operación, la base y el subject, y reescribe con su resultado")
    void subjectDescribesAndUpdatesWithSummary() {
        Target target = new Target();
        when(summary.describe(any(), any(), any())).thenReturn("Baja de materia: Física I (1)");

        proxied(target).withSubject();

        verify(summary).describe(target.seen.id(), "Baja de materia", "Subject");
        verify(updater).update(target.seen.id(), "Baja de materia: Física I (1)");
    }

    @Test
    @DisplayName("con subject y un describe tardío el resumen parte de la descripción nueva, no de la del annotation")
    void subjectWithLateDescribeSummarizesFromNewBase() {
        Target target = new Target();
        when(summary.describe(any(), any(), any())).thenReturn("Final: A y B");

        proxied(target).withSubjectAndLateDescribe();

        verify(summary).describe(target.seen.id(), "Final", "Subject");
        verify(updater).update(target.seen.id(), "Final: A y B");
    }

    @Test
    @DisplayName("con subject, si el resumen devuelve la base y nada cambió, el updater no se llama")
    void subjectWithUnchangedSummaryDoesNotCallUpdater() {
        Target target = new Target();
        when(summary.describe(any(), any(), any())).thenReturn("Baja de materia");

        proxied(target).withSubject();

        verify(summary).describe(target.seen.id(), "Baja de materia", "Subject");
        verifyNoInteractions(updater);
    }

    @Test
    @DisplayName("con subject y el método falla igual resume lo ya commiteado y propaga la excepción")
    void subjectWithFailingMethodStillSummarizes() {
        Target target = new Target();
        when(summary.describe(any(), any(), any())).thenReturn("Baja de materia: Física I (1)");

        assertThatThrownBy(() -> proxied(target).withSubjectThenFail()).isInstanceOf(IllegalStateException.class);

        verify(updater).update(target.seen.id(), "Baja de materia: Física I (1)");
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("sin subject y sin describe no resuelve el resumen ni llama al updater")
    void noSubjectNoDescribeCallsNothing() {
        proxied(new Target()).stampWithoutDescribe();

        verify(summary, never()).describe(any(), any(), any());
        verifyNoInteractions(updater);
    }

    @Test
    @DisplayName("sin subject y con describe tardío reescribe con ese texto sin pasar por el resumen")
    void noSubjectLateDescribeSkipsSummary() {
        Target target = new Target();

        proxied(target).describeAfterStamp();

        verify(updater).update(target.seen.id(), "Final");
        verify(summary, never()).describe(any(), any(), any());
    }
}
