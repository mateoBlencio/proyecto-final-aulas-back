package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawSpecialty(
        @JsonProperty("especialid") Integer especialid,
        @JsonProperty("asEspecialidadNombre") String asEspecialidadNombre,
        @JsonProperty("Abreviatura") String abreviatura
) {}
