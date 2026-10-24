package ar.edu.utn.frc.siga.cameracount.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawProbe(
        @JsonProperty("conecta") boolean conecta,
        @JsonProperty("ancho") Integer ancho,
        @JsonProperty("alto") Integer alto,
        @JsonProperty("motivo") String motivo,
        @JsonProperty("duracion_ms") long duracionMs
) {}
