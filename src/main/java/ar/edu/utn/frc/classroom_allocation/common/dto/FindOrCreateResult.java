package ar.edu.utn.frc.classroom_allocation.common.dto;

public record FindOrCreateResult<T>(T entity, boolean created) {}
