package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleAssignmentScopeCheckIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void scopeCheck_isValidatedAgainstExistingRows() {
        Boolean validated = jdbc.queryForObject(
                "select convalidated from pg_constraint where conname = 'chk_usuario_rol_alcance'", Boolean.class);

        assertThat(validated).isTrue();
    }

    @Test
    void insert_buildingAssignmentWithoutBuilding_isRejected() {
        Long userId = jdbc.queryForObject("select id_usuario from usuario limit 1", Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into usuario_rol (id_usuario, rol, tipo_alcance, id_alcance) values (?, 'AUXILIAR_AULICO', 'BUILDING', null)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
