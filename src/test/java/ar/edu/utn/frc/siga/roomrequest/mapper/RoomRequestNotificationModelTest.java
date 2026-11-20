package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.notification.InformalRegister;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestNotificationModel (aislado)")
class RoomRequestNotificationModelTest {

    private static final String PROPUESTA_SINGULAR =
            "le proponemos la siguiente aula. Le solicitamos que confirme si resulta adecuada.";
    private static final String PROPUESTA_PLURAL =
            "le proponemos las siguientes aulas. Le solicitamos que confirme si resultan adecuadas.";
    private static final String RESPUESTA_SINGULAR = "Si el aula propuesta es adecuada, no es necesario que responda. "
            + "En caso contrario, responda este correo e indique el motivo.";
    private static final String RESPUESTA_PLURAL = "Si las aulas propuestas son adecuadas, no es necesario que responda. "
            + "En caso contrario, responda este correo e indique el motivo.";
    private static final String CONSECUENCIA_CAMBIO = "consideraremos aceptado el cambio y quedará efectuado.";
    private static final String CONSECUENCIA_RESERVA =
            "consideraremos aceptada la propuesta y la reserva quedará confirmada.";

    private static final AssignedClassroomDto AULA_A = new AssignedClassroomDto(21L, 101, "Pabellón Central", 40);
    private static final AssignedClassroomDto AULA_B = new AssignedClassroomDto(22L, 102, "Pabellón Central", 40);

    private final RoomRequestNotificationModel model = new RoomRequestNotificationModel();

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE: diaSemana y horario van por separado, con aula anterior y sin fecha")
    void regularRoomChangeConAulaAnterior() {
        SubjectResponseDto subject = new SubjectResponseDto(42L, 101, "Análisis Matemático I", "ANUAL", null, true, null);
        AssignedClassroomDto aulaNueva = new AssignedClassroomDto(1L, 5, "Pabellón 1", 40);
        AssignedClassroomDto aulaAnterior = new AssignedClassroomDto(2L, 3, "Pabellón 2", 30);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.REGULAR_ROOM_CHANGE, subject, 10L,
                List.of(new CommissionResponseDto(101L, "3K1", null, null)), null, DayOfWeek.MONDAY,
                LocalTime.of(18, 0), LocalTime.of(22, 0), 1, List.of(aulaNueva), aulaAnterior, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("pedidoId", 10L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de cambio de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 5 (Pabellón 1)")),
                entry("materia", "Análisis Matemático I (código 101)"),
                entry("comisiones", "3K1"),
                entry("comisionesLabel", "Comisión"),
                entry("diaSemana", "Todos los lunes"),
                entry("horario", "18:00 a 22:00"),
                entry("aulaAnterior", "Aula 3 (Pabellón 2)"),
                entry("asuntoTitulo", "Propuesta de cambio de aula"),
                entry("asuntoPartes", List.of("Análisis Matemático I", "3K1")),
                entry("parrafoConfirmacion",
                        "Para el cambio de aula de su pedido #10, le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada."),
                entry("notaAdicional", "El cambio rige para todas las clases de ese día hasta el fin del cuatrimestre.")));
    }

    @Test
    @DisplayName("ONE_TIME_ROOM_CHANGE: trae fecha y horario, no trae diaSemana")
    void oneTimeRoomChangeTraeFechaNoDiaSemana() {
        SubjectResponseDto subject = new SubjectResponseDto(55L, 205, "Bases de Datos I", "ANUAL", null, true, null);
        AssignedClassroomDto aulaNueva = new AssignedClassroomDto(3L, 8, "Edificio Anexo", 50);
        AssignedClassroomDto aulaAnterior = new AssignedClassroomDto(4L, 6, "Edificio Anexo", 45);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.ONE_TIME_ROOM_CHANGE, subject, 11L,
                List.of(), LocalDate.of(2026, 10, 14), null,
                LocalTime.of(18, 0), LocalTime.of(22, 0), 1, List.of(aulaNueva), aulaAnterior, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("pedidoId", 11L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de cambio de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 8 (Edificio Anexo)")),
                entry("materia", "Bases de Datos I (código 205)"),
                entry("fecha", "Miércoles 14/10/2026"),
                entry("horario", "18:00 a 22:00"),
                entry("aulaAnterior", "Aula 6 (Edificio Anexo)"),
                entry("asuntoTitulo", "Propuesta de cambio de aula"),
                entry("asuntoPartes", List.of("Bases de Datos I", "14/10")),
                entry("parrafoConfirmacion",
                        "Para el cambio de aula de su pedido #11, válido solo para esa fecha, le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada."),
                entry("notaAdicional", "Después de esa clase, la cursada vuelve al aula habitual.")));
    }

    @Test
    @DisplayName("FINAL_EXAM sin comisiones: el mapa no tiene comisiones ni comisionesLabel")
    void finalExamSinComisiones() {
        SubjectResponseDto subject = new SubjectResponseDto(60L, 310, "Sistemas Operativos", "ANUAL", null, true, null);
        AssignedClassroomDto aula = new AssignedClassroomDto(5L, 12, "Pabellón Argentina", 60);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.FINAL_EXAM, subject, 12L,
                List.of(), LocalDate.of(2026, 11, 20), null,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).doesNotContainKeys("comisiones", "comisionesLabel");
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("pedidoId", 12L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 12 (Pabellón Argentina)")),
                entry("materia", "Sistemas Operativos (código 310)"),
                entry("fecha", "Viernes 20/11/2026"),
                entry("horario", "09:00 a 11:00"),
                entry("asuntoTitulo", "Propuesta de aula"),
                entry("asuntoPartes", List.of("Final de Sistemas Operativos", "20/11")),
                entry("parrafoConfirmacion",
                        "Para su examen final (pedido #12), le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada.")));
    }

    @Test
    @DisplayName("PARTIAL_EXAM_IN_CLASS con dos comisiones: horario marca horario de cursado")
    void partialExamInClassConDosComisiones() {
        SubjectResponseDto subject = new SubjectResponseDto(70L, 220, "Programación II", "ANUAL", null, true, null);
        AssignedClassroomDto aula = new AssignedClassroomDto(6L, 15, "Pabellón Argentina", 35);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.PARTIAL_EXAM_IN_CLASS, subject, 13L,
                List.of(new CommissionResponseDto(102L, "4K2", null, null), new CommissionResponseDto(103L, "4K3", null, null)),
                null, null, LocalTime.of(14, 0), LocalTime.of(16, 0), 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("pedidoId", 13L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 15 (Pabellón Argentina)")),
                entry("materia", "Programación II (código 220)"),
                entry("comisiones", "4K2 y 4K3"),
                entry("comisionesLabel", "Comisiones"),
                entry("horario", "14:00 a 16:00 (horario de cursado)"),
                entry("asuntoTitulo", "Propuesta de aula"),
                entry("asuntoPartes", List.of("Parcial de Programación II", "4K2 y 4K3")),
                entry("parrafoConfirmacion",
                        "Para el parcial de su pedido #13, le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada.")));
    }

    @Test
    @DisplayName("PARTIAL_EXAM_OFF_SCHEDULE con tres comisiones: se listan con comas y 'y'")
    void partialExamOffScheduleConTresComisiones() {
        SubjectResponseDto subject = new SubjectResponseDto(80L, 330, "Redes de Datos", "ANUAL", null, true, null);
        AssignedClassroomDto aula = new AssignedClassroomDto(7L, 20, "Pabellón España", 80);
        RoomRequestItemDetailDto detail = detail(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, subject, 14L,
                List.of(new CommissionResponseDto(104L, "3K1", null, null), new CommissionResponseDto(105L, "3K2", null, null),
                        new CommissionResponseDto(106L, "3K5", null, null)),
                null, null, LocalTime.of(8, 0), LocalTime.of(10, 0), 1, List.of(aula), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry("docente", "Ada Lovelace"),
                entry("pedidoId", 14L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 20 (Pabellón España)")),
                entry("materia", "Redes de Datos (código 330)"),
                entry("comisiones", "3K1, 3K2 y 3K5"),
                entry("comisionesLabel", "Comisiones"),
                entry("horario", "08:00 a 10:00"),
                entry("asuntoTitulo", "Propuesta de aula"),
                entry("asuntoPartes", List.of("Parcial de Redes de Datos", "fuera de horario")),
                entry("parrafoConfirmacion",
                        "Para su parcial (pedido #14), le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada."),
                entry("notaAdicional", "La evaluación es fuera del horario habitual de cursado.")));
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
                entry("pedidoId", 15L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 1 (Aula Magna)")),
                entry("asuntoTitulo", "Propuesta de aula"),
                entry("asuntoPartes", List.of("Congreso / Conferencia")),
                entry("parrafoConfirmacion",
                        "Para su actividad (pedido #15), le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada.")));
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
                entry("pedidoId", 16L),
                entry("pedidoRespuesta", RESPUESTA_SINGULAR),
                entry("tituloBanner", "Propuesta de aula"),
                entry("aulasLabel", "Aula propuesta"),
                entry("aulas", List.of("Aula 2 (Pabellón Argentina)")),
                entry("observaciones", "Necesita pizarra adicional"),
                entry("asuntoTitulo", "Propuesta de aula"),
                entry("asuntoPartes", List.of("Otro")),
                entry("parrafoConfirmacion",
                        "Para su pedido #16, le proponemos la siguiente aula. "
                                + "Le solicitamos que confirme si resulta adecuada.")));
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE con dos aulas propuestas: etiqueta, párrafo y respuesta en plural")
    void regularRoomChangeConDosAulasEnPlural() {
        RoomRequestItemDetailDto detail = detail(RoomRequestType.REGULAR_ROOM_CHANGE, null, 12L,
                List.of(), null, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0), 2,
                List.of(AULA_A, AULA_B), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result)
                .containsEntry("tituloBanner", "Propuesta de cambio de aula")
                .containsEntry("aulasLabel", "Aulas propuestas")
                .containsEntry("aulas", List.of("Aula 101 (Pabellón Central)", "Aula 102 (Pabellón Central)"))
                .containsEntry("parrafoConfirmacion",
                        "Para el cambio de aula de su pedido #12, le proponemos las siguientes aulas. "
                                + "Le solicitamos que confirme si resultan adecuadas.")
                .containsEntry("pedidoRespuesta", RESPUESTA_PLURAL);
    }

    @Test
    @DisplayName("FINAL_EXAM con dos aulas propuestas: etiqueta, párrafo y respuesta en plural")
    void finalExamConDosAulasEnPlural() {
        RoomRequestItemDetailDto detail = detail(RoomRequestType.FINAL_EXAM, null, 12L,
                List.of(), LocalDate.of(2026, 11, 20), null, LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of(AULA_A, AULA_B), null, null, null);

        Map<String, Object> result = model.build(detail);

        assertThat(result)
                .containsEntry("tituloBanner", "Propuesta de aula")
                .containsEntry("aulasLabel", "Aulas propuestas")
                .containsEntry("parrafoConfirmacion",
                        "Para su examen final (pedido #12), le proponemos las siguientes aulas. "
                                + "Le solicitamos que confirme si resultan adecuadas.")
                .containsEntry("pedidoRespuesta", RESPUESTA_PLURAL);
    }

    @ParameterizedTest
    @EnumSource(RoomRequestType.class)
    @DisplayName("todo tipo: con un aula el párrafo va en singular y con dos en plural")
    void parrafoConcuerdaConLaCantidadDeAulasEnTodoTipo(RoomRequestType type) {
        String singular = (String) model.build(minimalDetail(type, 1, List.of(AULA_A), null)).get("parrafoConfirmacion");
        String plural = (String) model.build(minimalDetail(type, 2, List.of(AULA_A, AULA_B), null)).get("parrafoConfirmacion");

        assertThat(singular).contains("pedido #12").endsWith(PROPUESTA_SINGULAR);
        assertThat(plural).contains("pedido #12").endsWith(PROPUESTA_PLURAL);
    }

    @ParameterizedTest
    @EnumSource(RoomRequestType.class)
    @DisplayName("ningún tipo dice 'asign' en asunto, banner ni párrafo, ni muestra el número de solicitud")
    void ningunTipoHablaDeAsignacion(RoomRequestType type) {
        for (List<AssignedClassroomDto> aulas : List.of(List.of(AULA_A), List.of(AULA_A, AULA_B))) {
            Map<String, Object> result = model.build(minimalDetail(type, aulas.size(), aulas, null));

            assertThat(result).containsKeys("asuntoTitulo", "tituloBanner", "parrafoConfirmacion");
            assertThat((String) result.get("asuntoTitulo")).doesNotContainIgnoringCase("asign");
            assertThat((String) result.get("tituloBanner")).doesNotContainIgnoringCase("asign");
            assertThat((String) result.get("parrafoConfirmacion"))
                    .doesNotContainIgnoringCase("asign")
                    .doesNotContainIgnoringCase("solicitud");
        }
    }

    @ParameterizedTest
    @EnumSource(RoomRequestType.class)
    @DisplayName("ningún texto del modelo usa tuteo, voseo ni la palabra 'mail', en singular ni en plural")
    void ningunTextoUsaRegistroInformal(RoomRequestType type) {
        List<ClassroomOptionDto> unaNoDisponible = List.of(
                new ClassroomOptionDto(31L, 204, "Central", UnavailableReason.DEACTIVATED, true));
        List<ClassroomOptionDto> dosNoDisponibles = List.of(
                new ClassroomOptionDto(31L, 204, "Central", UnavailableReason.DEACTIVATED, true),
                new ClassroomOptionDto(32L, 305, "Norte", UnavailableReason.PARENT_DEACTIVATED, true));

        for (List<AssignedClassroomDto> aulas : List.of(List.of(AULA_A), List.of(AULA_A, AULA_B))) {
            List<ClassroomOptionDto> preferidas = aulas.size() == 1 ? unaNoDisponible : dosNoDisponibles;
            Map<String, Object> result = model.build(completeDetail(type, aulas, preferidas));

            assertThat(result).containsKeys("parrafoConfirmacion", "pedidoRespuesta", "plazoRespuesta",
                    "motivoMenosAulas", "aulasPedidasNoDisponibles", "aulasLabel", "tituloBanner", "asuntoTitulo");
            for (String texto : textsOf(result)) {
                assertThat(InformalRegister.find(texto))
                        .as("%s con %d aula(s): \"%s\"", type, aulas.size(), texto)
                        .isEmpty();
            }
        }
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "Pabellón Central no tenía tres aulas libres en ese horario.",
            "No había tres aulas libres.",
            "UTN reservó el Aula Magna para un acto."
    })
    @DisplayName("motivoMenosAulas conserva el motivo tal como lo cargó el operador, mayúscula inicial incluida")
    void motivoMenosAulasConservaElMotivoLiteral(String motivo) {
        RoomRequestItemDetailDto detail = detail(RoomRequestType.FINAL_EXAM, null, 17L,
                List.of(), null, null, null, null, 3, List.of(AULA_A, AULA_B), null, motivo, null);

        assertThat(model.build(detail)).containsEntry("motivoMenosAulas", "Solicitó 3 aulas y le proponemos 2: " + motivo);
    }

    @Test
    @DisplayName("motivoMenosAulas aparece solo si se proponen menos aulas de las pedidas")
    void motivoMenosAulasApareceSoloCuandoFaltanAulas() {
        String decisionReason = "No había tres aulas contiguas libres en ese horario.";
        RoomRequestItemDetailDto conFaltante = detail(RoomRequestType.FINAL_EXAM, null, 17L,
                List.of(), null, null, null, null, 3, List.of(AULA_A, AULA_B), null, decisionReason, null);
        RoomRequestItemDetailDto completo = detail(RoomRequestType.FINAL_EXAM, null, 17L,
                List.of(), null, null, null, null, 2, List.of(AULA_A, AULA_B), null, decisionReason, null);

        Map<String, Object> conFaltanteResult = model.build(conFaltante);
        Map<String, Object> completoResult = model.build(completo);

        assertThat(conFaltanteResult).containsEntry("motivoMenosAulas",
                "Solicitó 3 aulas y le proponemos 2: No había tres aulas contiguas libres en ese horario.");
        assertThat(completoResult).doesNotContainKey("motivoMenosAulas");

        Map<String, Object> conFaltanteSinMotivo = new LinkedHashMap<>(conFaltanteResult);
        conFaltanteSinMotivo.remove("motivoMenosAulas");
        assertThat(completoResult).containsExactlyInAnyOrderEntriesOf(conFaltanteSinMotivo);
    }

    @Test
    @DisplayName("aulaAnterior ausente si el pedido no tiene aula anterior registrada (previo a la migración V10)")
    void aulaAnteriorAusenteSiNoHayAulaAnteriorRegistrada() {
        SubjectResponseDto subject = new SubjectResponseDto(55L, 205, "Bases de Datos I", "ANUAL", null, true, null);
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

    @Test
    @DisplayName("sin plazo de aceptación no hay clave plazoRespuesta")
    void sinPlazoNoHayPlazoRespuesta() {
        assertThat(model.build(minimalDetail(RoomRequestType.FINAL_EXAM, 1, List.of(AULA_A), null)))
                .doesNotContainKey("plazoRespuesta");
    }

    @Test
    @DisplayName("cambio de aula con plazo: día de semana, hora de Buenos Aires y cambio efectuado")
    void plazoRespuestaCambioDeAula() {
        RoomRequestItemDetailDto detail = minimalDetail(RoomRequestType.ONE_TIME_ROOM_CHANGE, 1, List.of(AULA_A),
                Instant.parse("2026-11-24T21:00:00Z"));

        assertThat(model.build(detail)).containsEntry("plazoRespuesta",
                "Si no recibimos respuesta antes del martes 24/11/2026 a las 18:00, " + CONSECUENCIA_CAMBIO);
    }

    @Test
    @DisplayName("no-cambio con plazo: día de semana, hora de Buenos Aires y reserva confirmada")
    void plazoRespuestaNoCambio() {
        RoomRequestItemDetailDto detail = minimalDetail(RoomRequestType.FINAL_EXAM, 1, List.of(AULA_A),
                Instant.parse("2026-11-13T15:00:00Z"));

        assertThat(model.build(detail)).containsEntry("plazoRespuesta",
                "Si no recibimos respuesta antes del viernes 13/11/2026 a las 12:00, " + CONSECUENCIA_RESERVA);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2026-11-24T01:30:00Z, lunes 23/11/2026 a las 22:30",
            "2026-11-24T02:59:00Z, lunes 23/11/2026 a las 23:59",
            "2026-11-24T03:00:00Z, martes 24/11/2026 a las 00:00"
    })
    @DisplayName("plazo cerca de medianoche: día de semana y fecha salen de la hora argentina, no de UTC")
    void plazoRespuestaUsaElDiaArgentino(String deadlineUtc, String esperado) {
        RoomRequestItemDetailDto cambio = minimalDetail(RoomRequestType.REGULAR_ROOM_CHANGE, 1, List.of(AULA_A),
                Instant.parse(deadlineUtc));
        RoomRequestItemDetailDto noCambio = minimalDetail(RoomRequestType.CONFERENCE, 1, List.of(AULA_A),
                Instant.parse(deadlineUtc));

        assertThat(model.build(cambio)).containsEntry("plazoRespuesta",
                "Si no recibimos respuesta antes del " + esperado + ", " + CONSECUENCIA_CAMBIO);
        assertThat(model.build(noCambio)).containsEntry("plazoRespuesta",
                "Si no recibimos respuesta antes del " + esperado + ", " + CONSECUENCIA_RESERVA);
    }

    private static List<String> textsOf(Map<String, Object> result) {
        List<String> texts = new ArrayList<>();
        for (Object value : result.values()) {
            if (value instanceof String text) {
                texts.add(text);
            } else if (value instanceof List<?> list) {
                list.forEach(element -> texts.add(String.valueOf(element)));
            }
        }
        return texts;
    }

    /** Pedido con todas las filas opcionales encendidas: plazo, menos aulas que las pedidas, aula anterior,
     *  aulas pedidas dadas de baja, materia y comisión. */
    private static RoomRequestItemDetailDto completeDetail(RoomRequestType type, List<AssignedClassroomDto> assigned,
            List<ClassroomOptionDto> preferred) {
        SubjectResponseDto subject = new SubjectResponseDto(42L, 101, "Análisis Matemático I", "ANUAL", null, true, null);
        RoomRequestItemDetailHeaderDto header = new RoomRequestItemDetailHeaderDto(
                1L, type, null, "Ada Lovelace", "ada@frc.utn.edu.ar", null, subject, null, 1);
        RoomRequestItemResponseDto item = new RoomRequestItemResponseDto(
                12L, 1, null, null, null, "No había más aulas libres en ese horario.",
                List.of(new CommissionResponseDto(101L, "3K1", null, null)), LocalDate.of(2026, 11, 20),
                DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(22, 0),
                null, null, null, 3, null, null, null, null, null, "Necesita proyector",
                preferred, null, assigned, new AssignedClassroomDto(40L, 7, "Pabellón 2", 30), null,
                Instant.parse("2026-11-24T01:30:00Z"), null, null, null, null, null, null, List.of(), null);
        return new RoomRequestItemDetailDto(header, item, List.of(), List.of());
    }

    private static RoomRequestItemDetailDto minimalDetail(RoomRequestType type, int classroomCount,
            List<AssignedClassroomDto> assignedClassrooms, Instant acceptanceDeadline) {
        return detail(type, null, 12L, List.of(), LocalDate.of(2026, 11, 20), null,
                LocalTime.of(9, 0), LocalTime.of(11, 0), classroomCount, assignedClassrooms, null, null, null,
                acceptanceDeadline);
    }

    private static RoomRequestItemDetailDto detail(RoomRequestType type, SubjectResponseDto subject, Long itemId,
            List<CommissionResponseDto> commissions, LocalDate date, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalTime endTime, Integer classroomCount,
            List<AssignedClassroomDto> assignedClassrooms, AssignedClassroomDto previousClassroom,
            String decisionReason, String observations) {
        return detail(type, subject, itemId, commissions, date, dayOfWeek, startTime, endTime, classroomCount,
                assignedClassrooms, previousClassroom, decisionReason, observations, null);
    }

    private static RoomRequestItemDetailDto detail(RoomRequestType type, SubjectResponseDto subject, Long itemId,
            List<CommissionResponseDto> commissions, LocalDate date, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalTime endTime, Integer classroomCount,
            List<AssignedClassroomDto> assignedClassrooms, AssignedClassroomDto previousClassroom,
            String decisionReason, String observations, Instant acceptanceDeadline) {
        RoomRequestItemDetailHeaderDto header = new RoomRequestItemDetailHeaderDto(
                1L, type, null, "Ada Lovelace", "ada@frc.utn.edu.ar", null, subject, null, 1);
        RoomRequestItemResponseDto item = new RoomRequestItemResponseDto(
                itemId, 1, null, null, null, decisionReason, commissions, date, dayOfWeek, startTime, endTime,
                null, null, null, classroomCount, null, null, null, null, null, observations,
                List.of(), null, assignedClassrooms, previousClassroom, null, acceptanceDeadline, null, null, null, null, null, null, List.of(), null);
        return new RoomRequestItemDetailDto(header, item, List.of(), List.of());
    }
}
