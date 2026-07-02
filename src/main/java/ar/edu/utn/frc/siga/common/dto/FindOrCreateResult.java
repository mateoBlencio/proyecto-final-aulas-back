package ar.edu.utn.frc.siga.common.dto;

public record FindOrCreateResult<T>(T entity, boolean created) {}
