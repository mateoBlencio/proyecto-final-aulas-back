package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ViewSyncRunner.requireRows")
class ViewSyncRunnerTest {

    @Test
    @DisplayName("requireRows: con lista vacía lanza SysacadEmptyViewException nombrando la vista")
    void requireRowsWithEmptyListThrowsNamingTheView() {
        assertThatThrownBy(() -> ViewSyncRunner.requireRows(List.of(), SysacadView.AULAS))
                .isInstanceOf(SysacadEmptyViewException.class)
                .hasMessageContaining(SysacadView.AULAS.name());
    }

    @Test
    @DisplayName("requireRows: con filas devuelve la misma lista sin modificarla")
    void requireRowsWithRowsReturnsSameList() {
        List<String> rows = List.of("a", "b");

        assertThat(ViewSyncRunner.requireRows(rows, SysacadView.AULAS)).isSameAs(rows);
    }
}
