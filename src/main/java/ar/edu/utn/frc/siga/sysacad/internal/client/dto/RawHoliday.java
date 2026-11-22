package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawHoliday(
        @JsonProperty("Fecha") String fecha,
        @JsonProperty("Descripcion") String descripcion
) {}
