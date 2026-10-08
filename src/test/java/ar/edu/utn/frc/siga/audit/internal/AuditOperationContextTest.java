package ar.edu.utn.frc.siga.audit.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    // ---------- describe ----------

    @Test
    @DisplayName("describe dentro de una operación cambia la descripción en curso y conserva id y padre")
    void describeReplacesCurrentDescription() {
        AuditOperationContext.begin("Inicial", "parent-1");
        String id = AuditOperationContext.current().id();

        AuditOperationContext.describe("Final con datos");

        AuditOperationContext.Operation op = AuditOperationContext.current();
        assertThat(op.description()).isEqualTo("Final con datos");
        assertThat(op.id()).isEqualTo(id);
        assertThat(op.parentId()).isEqualTo("parent-1");
    }

    @Test
    @DisplayName("describe fuera de una operación no lanza ni abre una operación")
    void describeOutsideOperationIsNoOp() {
        assertThatCode(() -> AuditOperationContext.describe("Suelta")).doesNotThrowAnyException();

        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("describe con null conserva la descripción anterior")
    void describeNullKeepsDescription() {
        AuditOperationContext.begin("Inicial", null);

        AuditOperationContext.describe(null);

        assertThat(AuditOperationContext.current().description()).isEqualTo("Inicial");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    @DisplayName("describe con texto vacío o en blanco se ignora y queda la descripción anterior")
    void describeBlankKeepsDescription(String blank) {
        AuditOperationContext.begin("Inicial", null);
        AuditOperationContext.markStamped();

        AuditOperationContext.describe(blank);

        assertThat(AuditOperationContext.current().description()).isEqualTo("Inicial");
        assertThat(AuditOperationContext.end()).isNull();
    }

    @Test
    @DisplayName("describe dentro de una operación anidada se ignora")
    void describeInNestedOperationIsIgnored() {
        AuditOperationContext.begin("Externa", null);
        AuditOperationContext.begin("Interna", null);

        AuditOperationContext.describe("Pisada de la interna");

        assertThat(AuditOperationContext.current().description()).isEqualTo("Externa");
    }

    @Test
    @DisplayName("describe vuelve a funcionar cuando la operación anidada ya se cerró")
    void describeWorksAgainAfterNestedOperationCloses() {
        AuditOperationContext.begin("Externa", null);
        AuditOperationContext.begin("Interna", null);
        AuditOperationContext.end();

        AuditOperationContext.describe("Final de la externa");

        assertThat(AuditOperationContext.current().description()).isEqualTo("Final de la externa");
    }

    @Test
    @DisplayName("describe con 255 caracteres (el límite exacto) no se trunca")
    void describeAtLimitIsNotTruncated() {
        String text = "a".repeat(255);
        AuditOperationContext.begin("Inicial", null);

        AuditOperationContext.describe(text);

        assertThat(AuditOperationContext.current().description()).isEqualTo(text);
    }

    @Test
    @DisplayName("describe con 256 caracteres queda en 255: 254 originales más '…'")
    void describeOneOverLimitIsTruncatedTo255() {
        String text = "a".repeat(255) + "b";
        AuditOperationContext.begin("Inicial", null);

        AuditOperationContext.describe(text);

        assertThat(AuditOperationContext.current().description())
                .hasSize(255)
                .isEqualTo("a".repeat(254) + "…");
    }

    @Test
    @DisplayName("un par sustituto que cruza el borde de corte se descarta entero, sin dejar una mitad suelta")
    void truncateDoesNotSplitSurrogatePairAtBoundary() {
        // The emoji takes chars 253 and 254; a plain cut at 254 would keep only its high surrogate.
        String text = "a".repeat(253) + "\uD83D\uDE00" + "b".repeat(10);

        String truncated = AuditOperationContext.truncate(text);

        assertThat(truncated).isEqualTo("a".repeat(253) + "…");
        assertThat(truncated.length()).isLessThanOrEqualTo(255);
        assertThat(truncated.chars().filter(c -> Character.isSurrogate((char) c))).isEmpty();
    }

    @Test
    @DisplayName("un par sustituto que entra completo antes del corte se conserva")
    void truncateKeepsSurrogatePairThatFits() {
        // The emoji takes chars 252 and 253, right below the 254-char cut.
        String text = "a".repeat(252) + "\uD83D\uDE00" + "b".repeat(10);

        String truncated = AuditOperationContext.truncate(text);

        assertThat(truncated).isEqualTo("a".repeat(252) + "\uD83D\uDE00" + "…");
        assertThat(truncated).hasSize(255);
    }

    // ---------- end() and the pending rewrite ----------

    @Test
    @DisplayName("end devuelve null si describe corrió antes de sellar la primera revisión")
    void endReturnsNullWhenDescribedBeforeStamp() {
        AuditOperationContext.begin("Inicial", null);
        AuditOperationContext.describe("Final");
        AuditOperationContext.markStamped();

        assertThat(AuditOperationContext.end()).isNull();
    }

    @Test
    @DisplayName("end devuelve null si hubo revisión sellada pero nadie llamó a describe")
    void endReturnsNullWhenStampedWithoutDescribe() {
        AuditOperationContext.begin("Inicial", null);
        AuditOperationContext.markStamped();

        assertThat(AuditOperationContext.end()).isNull();
    }

    @Test
    @DisplayName("end devuelve el id y la descripción nueva si describe corrió después de sellar")
    void endReturnsPendingWhenDescribedAfterStamp() {
        AuditOperationContext.begin("Inicial", null);
        String id = AuditOperationContext.current().id();
        AuditOperationContext.markStamped();
        AuditOperationContext.describe("Final");

        AuditOperationContext.PendingDescription pending = AuditOperationContext.end();

        assertThat(pending).isEqualTo(new AuditOperationContext.PendingDescription(id, "Final"));
        assertThat(AuditOperationContext.current()).isNull();
    }

    @Test
    @DisplayName("el texto pendiente ya viene truncado a 255")
    void pendingDescriptionIsTruncated() {
        AuditOperationContext.begin("Inicial", null);
        AuditOperationContext.markStamped();
        AuditOperationContext.describe("x".repeat(300));

        assertThat(AuditOperationContext.end().description()).isEqualTo("x".repeat(254) + "…");
    }

    @Test
    @DisplayName("cerrar una operación anidada no devuelve pendiente; sí lo hace la más externa")
    void onlyOutermostEndReturnsPending() {
        AuditOperationContext.begin("Externa", null);
        AuditOperationContext.markStamped();
        AuditOperationContext.describe("Final");
        AuditOperationContext.begin("Interna", null);

        assertThat(AuditOperationContext.end()).isNull();
        assertThat(AuditOperationContext.current()).isNotNull();

        assertThat(AuditOperationContext.end()).isNotNull();
    }

    @Test
    @DisplayName("un describe de la interna ignorado no deja la externa sucia")
    void ignoredNestedDescribeDoesNotMakeOuterDirty() {
        AuditOperationContext.begin("Externa", null);
        AuditOperationContext.markStamped();
        AuditOperationContext.begin("Interna", null);
        AuditOperationContext.describe("Pisada");
        AuditOperationContext.end();

        assertThat(AuditOperationContext.end()).isNull();
    }

    @Test
    @DisplayName("markStamped fuera de una operación no lanza")
    void markStampedOutsideOperationIsNoOp() {
        assertThatCode(AuditOperationContext::markStamped).doesNotThrowAnyException();
    }
}
