package ar.edu.utn.frc.siga.audit.internal;

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
        AuditOperationContext.begin("Asignación en lote", null);

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
        AuditOperationContext.begin("Externa", null);
        String outerId = AuditOperationContext.current().id();

        AuditOperationContext.begin("Interna", null);
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

    @Test
    @DisplayName("begin con padre expone el parentId en la operación en curso")
    void beginWithParentExposesParentId() {
        AuditOperationContext.begin("Hija", "parent-op-1");

        assertThat(AuditOperationContext.current().parentId()).isEqualTo("parent-op-1");
        assertThat(AuditOperationContext.current().id()).isNotEqualTo("parent-op-1");
    }

    @Test
    @DisplayName("begin sin padre deja el parentId en null")
    void beginWithoutParentHasNullParentId() {
        AuditOperationContext.begin("Raíz", null);

        assertThat(AuditOperationContext.current().parentId()).isNull();
    }

    @Test
    @DisplayName("una operación anidada conserva el padre de la externa e ignora el suyo")
    void nestedBeginKeepsOuterParent() {
        AuditOperationContext.begin("Externa", "outer-parent");
        AuditOperationContext.begin("Interna", "inner-parent");

        assertThat(AuditOperationContext.current().parentId()).isEqualTo("outer-parent");

        AuditOperationContext.end();
        assertThat(AuditOperationContext.current().parentId()).isEqualTo("outer-parent");
    }

    @Test
    @DisplayName("una externa sin padre no adopta el padre de la interna")
    void nestedBeginDoesNotAdoptInnerParent() {
        AuditOperationContext.begin("Externa", null);
        AuditOperationContext.begin("Interna", "inner-parent");

        assertThat(AuditOperationContext.current().parentId()).isNull();
    }

    @Test
    @DisplayName("currentOperationId es null fuera de una operación")
    void currentOperationIdIsNullOutsideOperation() {
        assertThat(AuditOperationContext.currentOperationId()).isNull();
    }

    @Test
    @DisplayName("currentOperationId devuelve el id de la operación en curso y vuelve a null al cerrarla")
    void currentOperationIdReturnsCurrentId() {
        AuditOperationContext.begin("Raíz", null);

        assertThat(AuditOperationContext.currentOperationId()).isEqualTo(AuditOperationContext.current().id());

        AuditOperationContext.end();
        assertThat(AuditOperationContext.currentOperationId()).isNull();
    }
}
