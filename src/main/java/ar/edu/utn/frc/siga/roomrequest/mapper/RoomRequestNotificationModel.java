package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arma el modelo del mail "pedido resuelto" (template {@code room-request-resolved}) a partir del
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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public Map<String, Object> build(RoomRequestItemDetailDto detail) {
        RoomRequestItemDetailHeaderDto request = detail.request();
        RoomRequestItemResponseDto item = detail.item();
        RoomRequestType type = request.type();
        boolean roomChange = type == RoomRequestType.ONE_TIME_ROOM_CHANGE || type == RoomRequestType.REGULAR_ROOM_CHANGE;

        SubjectResponseDto subject = request.subject();
        String materiaConCodigo = subject != null ? "%s (código %d)".formatted(subject.name(), subject.code()) : null;
        String materiaNombre = subject != null ? subject.name() : null;
        String comisiones = comisiones(item.commissions());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("docente", request.teacherName());
        model.put("tituloBanner", roomChange ? "Cambio de aula confirmado" : "Aula confirmada");
        model.put("aulasLabel", roomChange ? "Aula nueva" : (item.assignedClassrooms().size() > 1 ? "Aulas" : "Aula"));
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
            model.put("motivoMenosAulas", "Pediste %d aulas y asignamos %d: %s"
                    .formatted(item.classroomCount(), item.assignedClassrooms().size(), item.decisionReason()));
        }
        if (roomChange && item.previousClassroom() != null) {
            model.put("aulaAnterior", formatClassroom(item.previousClassroom()));
        }

        putPorTipo(model, type, item.id(), materiaNombre, comisiones, item.date());
        return model;
    }

    private void putPorTipo(Map<String, Object> model, RoomRequestType type, Long itemId,
                             String materiaNombre, String comisiones, LocalDate date) {
        switch (type) {
            case REGULAR_ROOM_CHANGE -> {
                model.put("asuntoTitulo", "Cambio de aula regular");
                model.put("asuntoPartes", asuntoPartes(materiaNombre, comisiones));
                model.put("parrafoConfirmacion", "Confirmamos el cambio de aula de tu pedido #%d.".formatted(itemId));
                model.put("notaAdicional", "Rige para todas las clases de ese día hasta fin del cuatrimestre.");
            }
            case ONE_TIME_ROOM_CHANGE -> {
                model.put("asuntoTitulo", "Cambio de aula");
                model.put("asuntoPartes", asuntoPartes(materiaNombre, formatShortDate(date)));
                model.put("parrafoConfirmacion",
                        "Confirmamos el cambio de aula de tu pedido #%d, solo para esa fecha.".formatted(itemId));
                model.put("notaAdicional", "Después de esa clase volvés al aula habitual.");
            }
            case FINAL_EXAM -> {
                model.put("asuntoTitulo", "Aula confirmada");
                model.put("asuntoPartes",
                        asuntoPartes(materiaNombre != null ? "Final de " + materiaNombre : null, formatShortDate(date)));
                model.put("parrafoConfirmacion", "Confirmamos el aula para tu examen final, pedido #%d.".formatted(itemId));
            }
            case PARTIAL_EXAM_IN_CLASS -> {
                model.put("asuntoTitulo", "Aula confirmada");
                model.put("asuntoPartes",
                        asuntoPartes(materiaNombre != null ? "Parcial de " + materiaNombre : null, comisiones));
                model.put("parrafoConfirmacion", "Confirmamos el aula para el parcial de tu pedido #%d.".formatted(itemId));
            }
            case PARTIAL_EXAM_OFF_SCHEDULE -> {
                model.put("asuntoTitulo", "Aula confirmada");
                model.put("asuntoPartes",
                        asuntoPartes(materiaNombre != null ? "Parcial de " + materiaNombre : null, "fuera de horario"));
                model.put("parrafoConfirmacion", "Confirmamos el aula para tu parcial, pedido #%d.".formatted(itemId));
                model.put("notaAdicional", "Es fuera del horario habitual de cursado.");
            }
            case CONFERENCE -> {
                model.put("asuntoTitulo", "Aula confirmada");
                model.put("asuntoPartes", List.of("Congreso / Conferencia"));
                model.put("parrafoConfirmacion", "Confirmamos el aula para tu actividad, pedido #%d.".formatted(itemId));
            }
            case OTHER -> {
                model.put("asuntoTitulo", "Aula confirmada");
                model.put("asuntoPartes", List.of("Otro"));
                model.put("parrafoConfirmacion", "Confirmamos el aula para tu pedido #%d.".formatted(itemId));
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
