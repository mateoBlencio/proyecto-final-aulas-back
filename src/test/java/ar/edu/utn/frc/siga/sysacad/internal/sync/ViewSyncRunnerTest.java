package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ViewSyncRunner.requireRows")
class ViewSyncRunnerTest {

    @Test
    @DisplayName("requireRows: con lista vacía lanza SysacadEmptyViewException con el nombre visible de la vista")
    void requireRowsWithEmptyListThrowsNamingTheView() {
        assertThatThrownBy(() -> ViewSyncRunner.requireRows(List.of(), SysacadView.AULAS))
                .isInstanceOf(SysacadEmptyViewException.class)
                .hasMessage("Los datos de aulas de SysAcad vinieron vacíos: se aborta el sync para no marcar todo como ausente");
    }

    @Test
    @DisplayName("requireRows: con filas devuelve la misma lista sin modificarla")
    void requireRowsWithRowsReturnsSameList() {
        List<String> rows = List.of("a", "b");

        assertThat(ViewSyncRunner.requireRows(rows, SysacadView.AULAS)).isSameAs(rows);
    }

    @ParameterizedTest
    @CsvSource({"EDIFICIOS,edificios", "AULAS,aulas", "ESPECIALIDADES,especialidades", "MATERIAS,materias",
            "COMISIONES,comisiones", "EVENTOS,eventos académicos", "ASIGNACIONES,horarios de aulas", "FERIADOS,feriados"})
    @DisplayName("requireRows: el mensaje de cada vista usa su nombre visible y no el código de la vista")
    void requireRowsMessageUsesTheViewLabel(SysacadView view, String label) {
        assertThat(view.label()).isEqualTo(label);

        assertThatThrownBy(() -> ViewSyncRunner.requireRows(List.of(), view))
                .isInstanceOf(SysacadEmptyViewException.class)
                .hasMessage("Los datos de " + label
                        + " de SysAcad vinieron vacíos: se aborta el sync para no marcar todo como ausente")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(view.name()));
    }
}
