package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestNotificationModel: aulas pedidas no disponibles")
class RoomRequestNotificationModelUnavailableTest {

    private static final String KEY = "aulasPedidasNoDisponibles";

    private final RoomRequestNotificationModel model = new RoomRequestNotificationModel();

    @Test
    @DisplayName("una preferida dada de baja y una asignada distinta: texto singular exacto")
    void preferidaDadaDeBajaSingular() {
        Map<String, Object> result = model.build(detail(
                List.of(new ClassroomOptionDto(1L, 204, "Central", UnavailableReason.DEACTIVATED, true)),
                List.of(new AssignedClassroomDto(2L, 301, "Norte", 40))));

        assertThat(result).containsEntry(KEY, "El aula 204 (Central) que solicitó no está disponible por el momento.");
    }

    @Test
    @DisplayName("dos preferidas dadas de baja (una por el edificio) y una asignada distinta: texto plural exacto")
    void preferidasDadasDeBajaPlural() {
        Map<String, Object> result = model.build(detail(
                List.of(new ClassroomOptionDto(1L, 204, "Central", UnavailableReason.DEACTIVATED, true),
                        new ClassroomOptionDto(3L, 305, "Norte", UnavailableReason.PARENT_DEACTIVATED, true)),
                List.of(new AssignedClassroomDto(2L, 301, "Sur", 40))));

        assertThat(result).containsEntry(KEY,
                "Las aulas 204 (Central) y 305 (Norte) que solicitó no están disponibles por el momento.");
    }

    @Test
    @DisplayName("si una asignada es preferida, la clave no aparece aunque otra preferida este dada de baja")
    void asignadaPreferidaApagaLaLinea() {
        Map<String, Object> result = model.build(detail(
                List.of(new ClassroomOptionDto(1L, 204, "Central", UnavailableReason.DEACTIVATED, true),
                        new ClassroomOptionDto(4L, 210, "Central", null, true)),
                List.of(new AssignedClassroomDto(4L, 210, "Central", 40))));

        assertThat(result).doesNotContainKey(KEY);
    }

    @Test
    @DisplayName("una preferida inhabilitada en SysAcad, sin baja en SIGA, no genera la linea")
    void inhabilitadaEnSysacadNoGeneraLinea() {
        Map<String, Object> result = model.build(detail(
                List.of(new ClassroomOptionDto(1L, 204, "Central", null, false)),
                List.of(new AssignedClassroomDto(2L, 301, "Norte", 40))));

        assertThat(result).doesNotContainKey(KEY);
    }

    @Test
    @DisplayName("sin preferidas no genera la linea")
    void sinPreferidasNoGeneraLinea() {
        Map<String, Object> result = model.build(detail(
                List.of(), List.of(new AssignedClassroomDto(2L, 301, "Norte", 40))));

        assertThat(result).doesNotContainKey(KEY);
    }

    private static RoomRequestItemDetailDto detail(List<ClassroomOptionDto> preferred,
            List<AssignedClassroomDto> assigned) {
        RoomRequestItemDetailHeaderDto header = new RoomRequestItemDetailHeaderDto(
                1L, RoomRequestType.PARTIAL_EXAM_IN_CLASS, null, "Ada Lovelace", "ada@frc.utn.edu.ar", null, null, null, 1);
        RoomRequestItemResponseDto item = new RoomRequestItemResponseDto(
                10L, 1, null, null, null, null, null, List.of(),LocalDate.of(2026, 10, 14), null,
                LocalTime.of(18, 0), LocalTime.of(22, 0), null, null, null, 1, null, null, null, null, null, null,
                preferred, null, assigned, null, null, null, null, null, null, null, null, null, List.of(), null);
        return new RoomRequestItemDetailDto(header, item, List.of(), List.of());
    }
}
