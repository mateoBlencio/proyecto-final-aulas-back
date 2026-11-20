package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Map;

public class ResourceNotFoundException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String GENERIC_RESOURCE = "el recurso";

    private static final Map<String, String> RESOURCE_NAMES = Map.ofEntries(
            Map.entry("User", "el usuario"),
            Map.entry("RoleAssignment", "la asignación de rol"),
            Map.entry("Occurrence", "la ocurrencia"),
            Map.entry("AcademicEvent", "el evento académico"),
            Map.entry("UniqueEvent", "el evento único"),
            Map.entry("Subject", "la materia"),
            Map.entry("Commission", "la comisión"),
            Map.entry("SubjectCommission", "la comisión de la materia"),
            Map.entry("StudyPlan", "el plan de estudio"),
            Map.entry("Specialty", "la especialidad"),
            Map.entry("AcademicPeriod", "el período académico"),
            Map.entry("SysacadSchedule", "el horario de SysAcad"),
            Map.entry("ArchivedCycle", "el ciclo archivado"),
            Map.entry("Allocation", "la asignación"),
            Map.entry("Classroom", "el aula"),
            Map.entry("ClassroomType", "el tipo de aula"),
            Map.entry("ResourceType", "el tipo de recurso"),
            Map.entry("Building", "el edificio"),
            Map.entry("RoomRequestItem", "el pedido de aula"));

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "Recurso no encontrado", message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(
                "No existe " + RESOURCE_NAMES.getOrDefault(resource, GENERIC_RESOURCE) + " " + id + ".");
    }
}
