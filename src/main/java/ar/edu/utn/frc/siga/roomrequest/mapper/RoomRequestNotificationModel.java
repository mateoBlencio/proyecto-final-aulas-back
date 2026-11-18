package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Arma el modelo del mail "propuesta de aula" (template {@code room-request-resolved}) a partir del
 *  {@link RoomRequestItemDetailDto} que ya compone {@link RoomRequestComposer}. Una clave ausente del
 *  mapa apaga su fila en el template ({@code th:if}): nunca se pone cadena vacía ni "—". */
@Component
public class RoomRequestNotificationModel {

    private static final Map<DayOfWeek, String> DAYS_ES = Map.of(
            DayOfWeek.MONDAY, "lunes",
            DayOfWeek.TUESDAY, "martes",
            DayOfWeek.WEDNESDAY, "miércoles",
            DayOfWeek.THURSDAY, "jueves",
            DayOfWeek.FRIDAY, "viernes",
            DayOfWeek.SATURDAY, "sábado",
            DayOfWeek.SUNDAY, "domingo");

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final ZoneId LOCAL_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public Map<String, Object> build(RoomRequestItemDetailDto detail) {
        RoomRequestItemDetailHeaderDto request = detail.request();
        RoomRequestItemResponseDto item = detail.item();
        RoomRequestType type = request.type();
        boolean roomChange = type == RoomRequestType.ONE_TIME_ROOM_CHANGE || type == RoomRequestType.REGULAR_ROOM_CHANGE;
        boolean singleClassroom = item.assignedClassrooms().size() == 1;

        SubjectResponseDto subject = request.subject();
        String materiaConCodigo = subject != null ? "%s (código %d)".formatted(subject.name(), subject.code()) : null;
        String materiaNombre = subject != null ? subject.name() : null;
        String comisiones = comisiones(item.commissions());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("docente", request.teacherName());
        model.put("tituloBanner", roomChange ? "Propuesta de cambio de aula" : "Propuesta de aula");
        model.put("aulasLabel", singleClassroom ? "Aula propuesta" : "Aulas propuestas");
        model.put("aulas", item.assignedClassrooms().stream().map(this::formatClassroom).toList());

        putIfPresent(model, "materia", materiaConCodigo);
        putIfPresent(model, "comisiones", comisiones);
        if (comisiones != null) {
            model.put("comisionesLabel", item.commissions().size() > 1 ? "Comisiones" : "Comisión");
        }
        putIfPresent(model, "observaciones", blankToNull(item.observations()));
        putIfPresent(model, "fecha", formatLongDate(item.date()));
        if (type == RoomRequestType.REGULAR_ROOM_CHANGE && item.dayOfWeek() != null) {
            model.put("diaSemana", "Todos los " + DAYS_ES.get(item.dayOfWeek()));
        }
        putIfPresent(model, "horario", formatHorario(type, item.startTime(), item.endTime()));

        if (item.assignedClassrooms().size() < item.classroomCount()) {
            model.put("motivoMenosAulas", "Solicitó %d aulas y le proponemos %d: %s"
                    .formatted(item.classroomCount(), item.assignedClassrooms().size(), item.decisionReason()));
        }
        putUnavailablePreferred(model, item);
        if (roomChange && item.previousClassroom() != null) {
            model.put("aulaAnterior", formatClassroom(item.previousClassroom()));
        }

        String propuesta = singleClassroom
                ? "le proponemos la siguiente aula. Le solicitamos que confirme si resulta adecuada."
                : "le proponemos las siguientes aulas. Le solicitamos que confirme si resultan adecuadas.";
        putPorTipo(model, type, item.id(), materiaNombre, comisiones, item.date(), propuesta);
        model.put("pedidoId", item.id());
        model.put("pedidoRespuesta", (singleClassroom
                ? "Si el aula propuesta es adecuada"
                : "Si las aulas propuestas son adecuadas")
                + ", no es necesario que responda. En caso contrario, responda este correo e indique el motivo.");
        if (item.acceptanceDeadline() != null) {
            String consecuencia = roomChange
                    ? "consideraremos aceptado el cambio y quedará efectuado"
                    : "consideraremos aceptada la propuesta y la reserva quedará confirmada";
            model.put("plazoRespuesta", "Si no recibimos respuesta antes del %s, %s."
                    .formatted(formatDeadline(item.acceptanceDeadline()), consecuencia));
        }
        return model;
    }

    private String formatDeadline(Instant deadline) {
        ZonedDateTime local = deadline.atZone(LOCAL_ZONE);
        return DAYS_ES.get(local.getDayOfWeek()) + " " + local.format(DEADLINE);
    }

    private void putUnavailablePreferred(Map<String, Object> model, RoomRequestItemResponseDto item) {
        List<ClassroomOptionDto> unavailable = item.preferredClassrooms().stream()
                .filter(c -> c.unavailableReason() == UnavailableReason.DEACTIVATED
                        || c.unavailableReason() == UnavailableReason.PARENT_DEACTIVATED)
                .toList();
        Set<Long> preferredIds = item.preferredClassrooms().stream().map(ClassroomOptionDto::id)
                .collect(Collectors.toSet());
        boolean assignedPreferred = item.assignedClassrooms().stream().anyMatch(a -> preferredIds.contains(a.id()));
        if (unavailable.isEmpty() || assignedPreferred) {
            return;
        }
        String classrooms = joinSpanish(unavailable.stream()
                .map(c -> "%d (%s)".formatted(c.roomNumber(), c.buildingName())).toList());
        model.put("aulasPedidasNoDisponibles", unavailable.size() == 1
                ? "El aula %s que solicitó no está disponible por el momento.".formatted(classrooms)
                : "Las aulas %s que solicitó no están disponibles por el momento.".formatted(classrooms));
    }

    private void putPorTipo(Map<String, Object> model, RoomRequestType type, Long itemId, String materiaNombre,
                             String comisiones, LocalDate date, String propuesta) {
        switch (type) {
            case REGULAR_ROOM_CHANGE -> {
                model.put("asuntoTitulo", "Propuesta de cambio de aula");
                model.put("asuntoPartes", asuntoPartes(materiaNombre, comisiones));
                model.put("parrafoConfirmacion", "Para el cambio de aula de su pedido #%d, %s".formatted(itemId, propuesta));
                model.put("notaAdicional", "El cambio rige para todas las clases de ese día hasta el fin del cuatrimestre.");
            }
            case ONE_TIME_ROOM_CHANGE -> {
                model.put("asuntoTitulo", "Propuesta de cambio de aula");
                model.put("asuntoPartes", asuntoPartes(materiaNombre, formatShortDate(date)));
                model.put("parrafoConfirmacion",
                        "Para el cambio de aula de su pedido #%d, válido solo para esa fecha, %s".formatted(itemId, propuesta));
                model.put("notaAdicional", "Después de esa clase, la cursada vuelve al aula habitual.");
            }
            case FINAL_EXAM -> {
                model.put("asuntoTitulo", "Propuesta de aula");
                model.put("asuntoPartes",
                        asuntoPartes(materiaNombre != null ? "Final de " + materiaNombre : null, formatShortDate(date)));
                model.put("parrafoConfirmacion", "Para su examen final (pedido #%d), %s".formatted(itemId, propuesta));
            }
            case PARTIAL_EXAM_IN_CLASS -> {
                model.put("asuntoTitulo", "Propuesta de aula");
                model.put("asuntoPartes",
                        asuntoPartes(materiaNombre != null ? "Parcial de " + materiaNombre : null, comisiones));
                model.put("parrafoConfirmacion", "Para el parcial de su pedido #%d, %s".formatted(itemId, propuesta));
            }
            case PARTIAL_EXAM_OFF_SCHEDULE -> {
                model.put("asuntoTitulo", "Propuesta de aula");
                model.put("asuntoPartes",
                        asuntoPartes(materiaNombre != null ? "Parcial de " + materiaNombre : null, "fuera de horario"));
                model.put("parrafoConfirmacion", "Para su parcial (pedido #%d), %s".formatted(itemId, propuesta));
                model.put("notaAdicional", "La evaluación es fuera del horario habitual de cursado.");
            }
            case CONFERENCE -> {
                model.put("asuntoTitulo", "Propuesta de aula");
                model.put("asuntoPartes", List.of("Congreso / Conferencia"));
                model.put("parrafoConfirmacion", "Para su actividad (pedido #%d), %s".formatted(itemId, propuesta));
            }
            case OTHER -> {
                model.put("asuntoTitulo", "Propuesta de aula");
                model.put("asuntoPartes", List.of("Otro"));
                model.put("parrafoConfirmacion", "Para su pedido #%d, %s".formatted(itemId, propuesta));
            }
        }
    }

    private String comisiones(List<CommissionResponseDto> commissions) {
        if (commissions.isEmpty()) {
            return null;
        }
        return joinSpanish(commissions.stream().map(CommissionResponseDto::courseCode).toList());
    }

    private String joinSpanish(List<String> values) {
        if (values.size() == 1) {
            return values.getFirst();
        }
        return String.join(", ", values.subList(0, values.size() - 1)) + " y " + values.getLast();
    }

    private String formatClassroom(AssignedClassroomDto classroom) {
        return "Aula %d (%s)".formatted(classroom.roomNumber(), classroom.buildingName());
    }

    private String formatLongDate(LocalDate date) {
        return date != null ? capitalize(DAYS_ES.get(date.getDayOfWeek())) + " " + date.format(LONG_DATE) : null;
    }

    private String formatShortDate(LocalDate date) {
        return date != null ? date.format(SHORT_DATE) : null;
    }

    private String formatHorario(RoomRequestType type, LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        String horario = "%s a %s".formatted(startTime.format(TIME), endTime.format(TIME));
        return type == RoomRequestType.PARTIAL_EXAM_IN_CLASS ? horario + " (horario de cursado)" : horario;
    }

    private List<String> asuntoPartes(String... parts) {
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void putIfPresent(Map<String, Object> model, String key, String value) {
        if (value != null) {
            model.put(key, value);
        }
    }
}
