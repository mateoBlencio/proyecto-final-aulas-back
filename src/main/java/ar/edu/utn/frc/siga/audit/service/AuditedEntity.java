package ar.edu.utn.frc.siga.audit.service;

public record AuditedEntity(
        Class<?> javaType,
        String jpaName,
        String label) {
}
