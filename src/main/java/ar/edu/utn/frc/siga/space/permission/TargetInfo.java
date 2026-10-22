package ar.edu.utn.frc.siga.space.permission;

/** Nombre del target de un permiso más datos de la especialidad (solo para SUBJECT; null en otros kinds). */
public record TargetInfo(
        String name,
        String specialtyName,
        String specialtyAbbreviation,
        Integer specialtyCode,
        Integer studyPlanCode
) {
    public static TargetInfo ofName(String name) {
        return new TargetInfo(name, null, null, null, null);
    }
}
