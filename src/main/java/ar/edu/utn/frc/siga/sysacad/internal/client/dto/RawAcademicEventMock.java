package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawAcademicEventMock(
        @JsonProperty("Curso") String curso,
        @JsonProperty("materia") Integer materia,
        @JsonProperty("Dia") Integer dia,
        @JsonProperty("HoraComienzo") String horaComienzo,
        @JsonProperty("DURACION") Integer duracion,
        @JsonProperty("HorarioCuatrimestre") Integer horarioCuatrimestre
) {}
