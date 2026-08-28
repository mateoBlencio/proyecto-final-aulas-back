package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewResponse<T>(List<T> rows) {}
