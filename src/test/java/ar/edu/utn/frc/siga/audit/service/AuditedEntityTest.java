package ar.edu.utn.frc.siga.audit.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditedEntity.convertId")
class AuditedEntityTest {

    private static AuditedEntity withIdType(Class<?> idType) {
        return new AuditedEntity(Object.class, "X", "Equis", "x_aud", "id", idType, List.of());
    }

    @Test
    @DisplayName("LocalDate: una fecha ISO se convierte a LocalDate")
    void convertId_localDate_parsesIsoDate() {
        assertThat(withIdType(LocalDate.class).convertId("2026-07-09")).isEqualTo(LocalDate.of(2026, 7, 9));
    }

    @Test
    @DisplayName("LocalDate: un formato dd/MM/yyyy lanza IllegalArgumentException")
    void convertId_localDate_invalidFormat_throwsIllegalArgument() {
        assertThatThrownBy(() -> withIdType(LocalDate.class).convertId("09/07/2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("LocalDate: una fecha inexistente lanza IllegalArgumentException")
    void convertId_localDate_impossibleDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> withIdType(LocalDate.class).convertId("2026-02-30"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Long, String y UUID siguen convirtiéndose")
    void convertId_otherTypes_stillConverted() {
        UUID uuid = UUID.randomUUID();
        assertThat(withIdType(Long.class).convertId("5")).isEqualTo(5L);
        assertThat(withIdType(String.class).convertId("clave")).isEqualTo("clave");
        assertThat(withIdType(UUID.class).convertId(uuid.toString())).isEqualTo(uuid);
    }
}
