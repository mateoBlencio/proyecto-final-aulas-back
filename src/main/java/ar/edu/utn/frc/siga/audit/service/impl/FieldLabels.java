package ar.edu.utn.frc.siga.audit.service.impl;

import java.util.Locale;
import java.util.Map;

/** Spanish names of the audited properties and of the enum constants shown in the field diff. */
final class FieldLabels {

    private static final Map<String, String> PROPERTIES = Map.ofEntries(
            Map.entry("year", "Año"), Map.entry("semester", "Cuatrimestre"),
            Map.entry("startDate", "Fecha de inicio"), Map.entry("endDate", "Fecha de fin"),
            Map.entry("recessStart", "Inicio del receso"), Map.entry("recessEnd", "Fin del receso"),
            Map.entry("courseCode", "Código de curso"), Map.entry("academicPeriod", "Período académico"),
            Map.entry("syncedAt", "Última sincronización"), Map.entry("sysacadHash", "Huella de Sysacad"),
            Map.entry("sysacadEnabled", "Habilitado en Sysacad"), Map.entry("sysacadCurrent", "Vigente en Sysacad"),
            Map.entry("version", "Versión"), Map.entry("date", "Fecha"), Map.entry("description", "Descripción"),
            Map.entry("specialtyCode", "Código de especialidad"), Map.entry("name", "Nombre"),
            Map.entry("abbreviation", "Abreviatura"), Map.entry("planCode", "Código de plan"),
            Map.entry("specialty", "Especialidad"), Map.entry("subject", "Materia"),
            Map.entry("commission", "Comisión"), Map.entry("enrolledCount", "Inscriptos"),
            Map.entry("inPerson", "Presencial"), Map.entry("deactivationReason", "Motivo de baja"),
            Map.entry("code", "Código"), Map.entry("studyPlan", "Plan de estudio"), Map.entry("term", "Cursada"),
            Map.entry("occurrenceId", "Ocurrencia"), Map.entry("classroomId", "Aula"),
            Map.entry("source", "Origen"), Map.entry("observation", "Observación"),
            Map.entry("executedAt", "Fecha de ejecución"), Map.entry("retainedFromCycle", "Ciclo retenido desde"),
            Map.entry("cutoff", "Fecha de corte"), Map.entry("status", "Estado"),
            Map.entry("occurrenceRows", "Filas de ocurrencias"), Map.entry("allocationRows", "Filas de asignaciones"),
            Map.entry("deletedRevisions", "Revisiones eliminadas"), Map.entry("errorMessage", "Mensaje de error"),
            Map.entry("user", "Usuario"), Map.entry("role", "Rol"), Map.entry("scopeType", "Tipo de alcance"),
            Map.entry("scopeId", "Alcance"), Map.entry("email", "Correo"), Map.entry("enabled", "Habilitado"),
            Map.entry("firstName", "Nombre"), Map.entry("lastName", "Apellido"), Map.entry("month", "Mes"),
            Map.entry("reason", "Motivo"), Map.entry("regeneratedAt", "Fecha de regeneración"),
            Map.entry("event", "Evento"), Map.entry("roomSlot", "Número de aula del evento"),
            Map.entry("mirrorOfOccurrenceId", "Ocurrencia espejada"), Map.entry("releaseReason", "Motivo de liberación"),
            Map.entry("releaseNote", "Nota de liberación"), Map.entry("previousClassroomId", "Aula anterior"),
            Map.entry("dayOfWeek", "Día de la semana"), Map.entry("kind", "Tipo"),
            Map.entry("requiresProjector", "Requiere proyector"), Map.entry("requiresComputers", "Requiere computadoras"),
            Map.entry("computerCount", "Cantidad de computadoras"), Map.entry("requiredSoftware", "Software requerido"),
            Map.entry("requiresExamUsers", "Requiere usuarios de examen"), Map.entry("item", "Pedido"),
            Map.entry("position", "Posición"), Map.entry("request", "Solicitud"),
            Map.entry("decidedBy", "Decidido por"), Map.entry("decidedAt", "Fecha de decisión"),
            Map.entry("decisionReason", "Motivo de la decisión"), Map.entry("commissionId", "Comisión"),
            Map.entry("sourceRecurringEventId", "Evento recurrente de origen"), Map.entry("startTime", "Hora de inicio"),
            Map.entry("duration", "Duración"), Map.entry("estimated", "Inscriptos estimados"),
            Map.entry("classroomCount", "Cantidad de aulas"), Map.entry("observations", "Observaciones"),
            Map.entry("derivedBuildingId", "Edificio derivado"), Map.entry("derivedAt", "Fecha de derivación"),
            Map.entry("returnedFromBuildingId", "Edificio que lo devolvió"),
            Map.entry("returnedReason", "Motivo de devolución"), Map.entry("notifiedAt", "Fecha de notificación"),
            Map.entry("type", "Tipo"), Map.entry("scope", "Ámbito"), Map.entry("teacherName", "Docente"),
            Map.entry("teacherEmail", "Correo del docente"), Map.entry("teacherPhone", "Teléfono del docente"),
            Map.entry("subjectId", "Materia"), Map.entry("glpiTicketId", "Ticket de GLPI"),
            Map.entry("key", "Clave"), Map.entry("value", "Valor"), Map.entry("buildingCode", "Código de edificio"),
            Map.entry("building", "Edificio"), Map.entry("roomNumber", "Número de aula"),
            Map.entry("capacity", "Capacidad"), Map.entry("classroomType", "Tipo de aula"),
            Map.entry("permissionMode", "Modo de permisos"), Map.entry("classroom", "Aula"),
            Map.entry("targetKind", "Tipo de destino"), Map.entry("targetId", "Destino"),
            Map.entry("resourceType", "Tipo de recurso"), Map.entry("quantity", "Cantidad"),
            Map.entry("valueKind", "Tipo de valor"));

    private static final Map<String, String> CONSTANTS = Map.ofEntries(
            Map.entry("MANUAL", "Manual"), Map.entry("AUTOMATIC", "Automática"), Map.entry("IMPORTED", "Importada"),
            Map.entry("SYSACAD", "Sysacad"), Map.entry("NO_ENROLLED", "Sin inscriptos"),
            Map.entry("NEEDS_ROOM", "Necesita aula"), Map.entry("ROOM_RELEASED", "Aula liberada"),
            Map.entry("VIRTUAL_CLASS", "Clase virtual"), Map.entry("CANCELLED", "Cancelada"),
            Map.entry("MIRROR_RETIRED", "Espejo retirado"), Map.entry("OTHER", "Otro"),
            Map.entry("SUBSECRETARIA", "Subsecretaría"), Map.entry("AUXILIAR_AULICO", "Auxiliar áulico"),
            Map.entry("CONSULTA", "Consulta"), Map.entry("GLOBAL", "Global"), Map.entry("BUILDING", "Edificio"),
            Map.entry("NEW", "Nuevo"), Map.entry("DERIVED_TO_BUILDING", "Derivado a edificio"),
            Map.entry("IN_EVALUATION", "En evaluación"), Map.entry("RESOLVED", "Resuelto"),
            Map.entry("ONE_TIME_ROOM_CHANGE", "Cambio de aula único"),
            Map.entry("REGULAR_ROOM_CHANGE", "Cambio de aula regular"),
            Map.entry("PARTIAL_EXAM_IN_CLASS", "Parcial en horario de clase"),
            Map.entry("PARTIAL_EXAM_OFF_SCHEDULE", "Parcial fuera de horario"),
            Map.entry("FINAL_EXAM", "Examen final"), Map.entry("CONFERENCE", "Conferencia"),
            Map.entry("GRADO", "Grado"), Map.entry("TECNICATURA", "Tecnicatura"),
            Map.entry("LICENCIATURA", "Licenciatura"), Map.entry("EXTENSION", "Extensión"),
            Map.entry("POSTGRADO", "Posgrado"), Map.entry("PARCIAL", "Parcial"),
            Map.entry("ALL", "Todas"), Map.entry("NONE", "Ninguna"), Map.entry("SUBSET", "Algunas"),
            Map.entry("SUBJECT", "Materia"), Map.entry("COUNT", "Cantidad"), Map.entry("BOOLEAN", "Sí/No"),
            Map.entry("IN_PROGRESS", "En curso"), Map.entry("COMPLETED", "Completado"),
            Map.entry("STOPPED_BY_CONCURRENT_RUN", "Detenido por otra ejecución"), Map.entry("FAILED", "Fallido"),
            Map.entry("MONDAY", "Lunes"), Map.entry("TUESDAY", "Martes"), Map.entry("WEDNESDAY", "Miércoles"),
            Map.entry("THURSDAY", "Jueves"), Map.entry("FRIDAY", "Viernes"), Map.entry("SATURDAY", "Sábado"),
            Map.entry("SUNDAY", "Domingo"));

    private FieldLabels() {
    }

    static String property(String property) {
        String label = PROPERTIES.get(property);
        return label != null ? label : humanize(property.replaceAll("([a-z0-9])([A-Z])", "$1 $2"));
    }

    static String constant(String constant) {
        String label = CONSTANTS.get(constant);
        return label != null ? label : humanize(constant.replace('_', ' '));
    }

    private static String humanize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
