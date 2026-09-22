package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestNotificationModel (aislado)")
class RoomRequestNotificationModelTest {

    private final RoomRequestNotificationModel model = new RoomRequestNotificationModel();

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE: diaSemana y horario van por separado, con aula anterior y sin fecha")
    void regularRoomChangeConAulaAnterior() {
        SubjectResponseDto subject = new SubjectResponseDto(42L, 101, "Análisis Matemático I", "ANUAL", null);
        AssignedClassroomDto aulaNueva = new AssignedClassroomDto(1L, 5, "Pabellón 1", 40);
        AssignedClassroomDto aulaAnterior = new AssignedClassroomDto(2L, 3, "Pabellón 2", 30);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.REGULAR_ROOM_CHANGE, subject, 10L,
                List.of(new CommissionResponseDto(101L, "3K1", null)), null, DayOfWeek.MONDAY,
                LocalTime.of(18, 0), LocalTime.of(22, 0), 1, List.of(aulaNueva), aulaAnterior, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Cambio de aula confirmado"),
                entry("aulasLabel", "Aula nueva"),
                entry("aulas", List.of("Aula 5 (Pabellón 1)")),
                entry("materia", "Análisis Matemático I (código 101)"),
                entry("comisiones", "3K1"),
                entry("comisionesLabel", "Comisión"),
                entry("diaSemana", "Todos los lunes"),
                entry("horario", "18:00 a 22:00"),
                entry("aulaAnterior", "Aula 3 (Pabellón 2)"),
                entry("asuntoTitulo", "Cambio de aula regular"),
                entry("asuntoPartes", List.of("Análisis Matemático I", "3K1")),
                entry("parrafoConfirmacion", "Confirmamos el cambio de aula de tu pedido #10."),
                entry("notaAdicional", "Rige para todas las clases de ese día hasta fin del cuatrimestre.")));
    }

    @Test
    @DisplayName("ONE_TIME_ROOM_CHANGE: trae fecha y horario, no trae diaSemana")
    void oneTimeRoomChangeTraeFechaNoDiaSemana() {
        SubjectResponseDto subject = new SubjectResponseDto(55L, 205, "Bases de Datos I", "ANUAL", null);
        AssignedClassroomDto aulaNueva = new AssignedClassroomDto(3L, 8, "Edificio Anexo", 50);
        AssignedClassroomDto aulaAnterior = new AssignedClassroomDto(4L, 6, "Edificio Anexo", 45);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.ONE_TIME_ROOM_CHANGE, subject, 11L,
                List.of(), LocalDate.of(2026, 10, 14), null,
                LocalTime.of(18, 0), LocalTime.of(22, 0), 1, List.of(aulaNueva), aulaAnterior, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Cambio de aula confirmado"),
                entry("aulasLabel", "Aula nueva"),
                entry("aulas", List.of("Aula 8 (Edificio Anexo)")),
                entry("materia", "Bases de Datos I (código 205)"),
                entry("fecha", "Miércoles 14/10/2026"),
                entry("horario", "18:00 a 22:00"),
                entry("aulaAnterior", "Aula 6 (Edificio Anexo)"),
                entry("asuntoTitulo", "Cambio de aula"),
                entry("asuntoPartes", List.of("Bases de Datos I", "14/10")),
                entry("parrafoConfirmacion", "Confirmamos el cambio de aula de tu pedido #11, solo para esa fecha."),
                entry("notaAdicional", "Después de esa clase volvés al aula habitual.")));
    }

    @Test
    @DisplayName("FINAL_EXAM sin comisiones: el mapa no tiene comisiones ni comisionesLabel")
    void finalExamSinComisiones() {
        SubjectResponseDto subject = new SubjectResponseDto(60L, 310, "Sistemas Operativos", "ANUAL", null);
        AssignedClassroomDto aula = new AssignedClassroomDto(5L, 12, "Pabellón Argentina", 60);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.FINAL_EXAM, subject, 12L,
                List.of(), LocalDate.of(2026, 11, 20), null,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).doesNotContainKeys("comisiones", "comisionesLabel");
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Aula confirmada"),
                entry("aulasLabel", "Aula"),
                entry("aulas", List.of("Aula 12 (Pabellón Argentina)")),
                entry("materia", "Sistemas Operativos (código 310)"),
                entry("fecha", "Viernes 20/11/2026"),
                entry("horario", "09:00 a 11:00"),
                entry("asuntoTitulo", "Aula confirmada"),
                entry("asuntoPartes", List.of("Final de Sistemas Operativos", "20/11")),
                entry("parrafoConfirmacion", "Confirmamos el aula para tu examen final, pedido #12.")));
    }

    @Test
    @DisplayName("PARTIAL_EXAM_IN_CLASS con dos comisiones: horario marca horario de cursado")
    void partialExamInClassConDosComisiones() {
        SubjectResponseDto subject = new SubjectResponseDto(70L, 220, "Programación II", "ANUAL", null);
        AssignedClassroomDto aula = new AssignedClassroomDto(6L, 15, "Pabellón Argentina", 35);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.PARTIAL_EXAM_IN_CLASS, subject, 13L,
                List.of(new CommissionResponseDto(102L, "4K2", null), new CommissionResponseDto(103L, "4K3", null)),
                null, null, LocalTime.of(14, 0), LocalTime.of(16, 0), 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Aula confirmada"),
                entry("aulasLabel", "Aula"),
                entry("aulas", List.of("Aula 15 (Pabellón Argentina)")),
                entry("materia", "Programación II (código 220)"),
                entry("comisiones", "4K2 y 4K3"),
                entry("comisionesLabel", "Comisiones"),
                entry("horario", "14:00 a 16:00 (horario de cursado)"),
                entry("asuntoTitulo", "Aula confirmada"),
                entry("asuntoPartes", List.of("Parcial de Programación II", "4K2 y 4K3")),
                entry("parrafoConfirmacion", "Confirmamos el aula para el parcial de tu pedido #13.")));
    }

    @Test
    @DisplayName("PARTIAL_EXAM_OFF_SCHEDULE con tres comisiones: se listan con comas y 'y'")
    void partialExamOffScheduleConTresComisiones() {
        SubjectResponseDto subject = new SubjectResponseDto(80L, 330, "Redes de Datos", "ANUAL", null);
        AssignedClassroomDto aula = new AssignedClassroomDto(7L, 20, "Pabellón España", 80);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, subject, 14L,
                List.of(new CommissionResponseDto(104L, "3K1", null), new CommissionResponseDto(105L, "3K2", null),
                        new CommissionResponseDto(106L, "3K5", null)),
                null, null, LocalTime.of(8, 0), LocalTime.of(10, 0), 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Aula confirmada"),
                entry("aulasLabel", "Aula"),
                entry("aulas", List.of("Aula 20 (Pabellón España)")),
                entry("materia", "Redes de Datos (código 330)"),
                entry("comisiones", "3K1, 3K2 y 3K5"),
                entry("comisionesLabel", "Comisiones"),
                entry("horario", "08:00 a 10:00"),
                entry("asuntoTitulo", "Aula confirmada"),
                entry("asuntoPartes", List.of("Parcial de Redes de Datos", "fuera de horario")),
                entry("parrafoConfirmacion", "Confirmamos el aula para tu parcial, pedido #14."),
                entry("notaAdicional", "Es fuera del horario habitual de cursado.")));
    }

    @Test
    @DisplayName("CONFERENCE sin materia: asuntoPartes es fijo y no hay clave materia")
    void conferenceSinMateria() {
        AssignedClassroomDto aula = new AssignedClassroomDto(8L, 1, "Aula Magna", 200);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.CONFERENCE, null, 15L,
                List.of(), null, null, null, null, 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).doesNotContainKey("materia");
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Aula confirmada"),
                entry("aulasLabel", "Aula"),
                entry("aulas", List.of("Aula 1 (Aula Magna)")),
                entry("asuntoTitulo", "Aula confirmada"),
                entry("asuntoPartes", List.of("Congreso / Conferencia")),
                entry("parrafoConfirmacion", "Confirmamos el aula para tu actividad, pedido #15.")));
    }

    @Test
    @DisplayName("OTHER sin fecha ni horario: no muestra esas filas y sí muestra observaciones")
    void otherSinFechaNiHorario() {
        AssignedClassroomDto aula = new AssignedClassroomDto(9L, 2, "Pabellón Argentina", 30);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.OTHER, null, 16L,
                List.of(), null, null, null, null, 1, List.of(aula), null, null, "Necesita pizarra adicional");

        Map<String, Object> result = model.build(detail);

        assertThat(result).doesNotContainKeys("fecha", "horario");
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("tituloBanner", "Aula confirmada"),
                entry("aulasLabel", "Aula"),
                entry("aulas", List.of("Aula 2 (Pabellón Argentina)")),
                entry("observaciones", "Necesita pizarra adicional"),
                entry("asuntoTitulo", "Aula confirmada"),
                entry("asuntoPartes", List.of("Otro")),
                entry("parrafoConfirmacion", "Confirmamos el aula para tu pedido #16.")));
    }

    @Test
    @DisplayName("motivoMenosAulas aparece solo si se asignaron menos aulas de las pedidas")
    void motivoMenosAulasApareceSoloCuandoFaltanAulas() {
        AssignedClassroomDto aula = new AssignedClassroomDto(10L, 4, "Pabellón Argentina", 30);
        String decisionReason = "No había dos aulas contiguas libres en ese horario.";
        RoomRequestItemDetailDto conFaltante = detail(RoomRequestType.FINAL_EXAM, null, 17L,
                List.of(), null, null, null, null, 2, List.of(aula), null, decisionReason, null);
        RoomRequestItemDetailDto completo = detail(RoomRequestType.FINAL_EXAM, null, 17L,
                List.of(), null, null, null, null, 1, List.of(aula), null, decisionReason, null);

        Map<String, Object> conFaltanteResult = model.build(conFaltante);
        Map<String, Object> completoResult = model.build(completo);

        assertThat(conFaltanteResult).containsEntry("motivoMenosAulas",
                "Pediste 2 aulas y asignamos 1: No había dos aulas contiguas libres en ese horario.");
        assertThat(completoResult).doesNotContainKey("motivoMenosAulas");

        Map<String, Object> conFaltanteSinMotivo = new LinkedHashMap<>(conFaltanteResult);
        conFaltanteSinMotivo.remove("motivoMenosAulas");
        assertThat(completoResult).containsExactlyInAnyOrderEntriesOf(conFaltanteSinMotivo);
    }

    @Test
    @DisplayName("aulaAnterior ausente si el pedido no tiene aula anterior registrada (previo a la migración V10)")
    void aulaAnteriorAusenteSiNoHayAulaAnteriorRegistrada() {
        SubjectResponseDto subject = new SubjectResponseDto(55L, 205, "Bases de Datos I", "ANUAL", null);
        AssignedClassroomDto aulaNueva = new AssignedClassroomDto(3L, 8, "Edificio Anexo", 50);
        AssignedClassroomDto aulaAnterior = new AssignedClassroomDto(4L, 6, "Edificio Anexo", 45);
        RoomRequestItemDetailDto conAulaAnterior = detail(RoomRequestType.ONE_TIME_ROOM_CHANGE, subject, 19L,
                List.of(), LocalDate.of(2026, 10, 14), null,
                LocalTime.of(18, 0), LocalTime.of(22, 0), 1, List.of(aulaNueva), aulaAnterior, null, null);
        RoomRequestItemDetailDto sinAulaAnterior = detail(RoomRequestType.ONE_TIME_ROOM_CHANGE, subject, 19L,
                List.of(), LocalDate.of(2026, 10, 14), null,
                LocalTime.of(18, 0), LocalTime.of(22, 0), 1, List.of(aulaNueva), null, null, null);

        Map<String, Object> conAulaAnteriorResult = model.build(conAulaAnterior);
        Map<String, Object> sinAulaAnteriorResult = model.build(sinAulaAnterior);

        assertThat(conAulaAnteriorResult).containsEntry("aulaAnterior", "Aula 6 (Edificio Anexo)");
        assertThat(sinAulaAnteriorResult).doesNotContainKey("aulaAnterior");

        Map<String, Object> conAulaAnteriorSinClave = new LinkedHashMap<>(conAulaAnteriorResult);
        conAulaAnteriorSinClave.remove("aulaAnterior");
        assertThat(sinAulaAnteriorResult).containsExactlyInAnyOrderEntriesOf(conAulaAnteriorSinClave);
    }

    private static RoomRequestItemDetailDto detail(RoomRequestType type, SubjectResponseDto subject, Long itemId,
            List<CommissionResponseDto> commissions, LocalDate date, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalTime endTime, Integer classroomCount,
            List<AssignedClassroomDto> assignedClassrooms, AssignedClassroomDto previousClassroom,
            String decisionReason, String observations) {
        RoomRequestItemDetailHeaderDto header = new RoomRequestItemDetailHeaderDto(
                1L, type, null, "Ada Lovelace", "ada@frc.utn.edu.ar", null, subject, null);
        RoomRequestItemResponseDto item = new RoomRequestItemResponseDto(
                itemId, 1, null, null, null, decisionReason, commissions, date, dayOfWeek, startTime, endTime,
                null, null, classroomCount, null, null, null, null, null, observations,
                List.of(), assignedClassrooms, previousClassroom, null, null, null, null, null);
        return new RoomRequestItemDetailDto(header, item);
    }
}
