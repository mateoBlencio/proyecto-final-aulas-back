package ar.edu.utn.frc.siga.audit.registry;

public record AuditedEntity(
        Class<?> javaType,
        String jpaName,
        String label) {
}
