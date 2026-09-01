package ar.edu.utn.frc.siga.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditOperationContext")
class AuditOperationContextTest {

    @AfterEach
    void clear() {
        while (AuditOperationContext.current() != null) {
            AuditOperationContext.end();
        }
    }

    @Test
    @DisplayName("sin begin no hay operación")
    void noOperationByDefault() {
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("begin/end abre y cierra una operación con id generado")
    void beginEndLifecycle() {
        AuditOperationContext.begin("Asignación en lote");

        AuditOperationContext.Operation op = AuditOperationContext.current();
        assertThat(op).isNotNull();
        assertThat(op.description()).isEqualTo("Asignación en lote");
        assertThat(op.id()).isNotBlank();

        AuditOperationContext.end();
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("anidar no abre una operación nueva: la más externa gana y sólo cierra al salir del todo")
    void nestedBeginKeepsOutermost() {
        AuditOperationContext.begin("Externa");
        String outerId = AuditOperationContext.current().id();

        AuditOperationContext.begin("Interna");
        assertThat(AuditOperationContext.current().id()).isEqualTo(outerId);
        assertThat(AuditOperationContext.current().description()).isEqualTo("Externa");

        AuditOperationContext.end();
        assertThat(AuditOperationContext.current()).isNotNull();
        assertThat(AuditOperationContext.current().id()).isEqualTo(outerId);

        AuditOperationContext.end();
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("end de más no rompe")
    void extraEndIsSafe() {
        AuditOperationContext.end();
        assertThat(AuditOperationContext.current()).isNull();
    }
}
