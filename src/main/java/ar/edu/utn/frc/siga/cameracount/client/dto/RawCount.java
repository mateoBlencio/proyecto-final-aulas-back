package ar.edu.utn.frc.siga.cameracount.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawCount(
        @JsonProperty("estado") String estado,
        @JsonProperty("cantidad") Integer cantidad,
        @JsonProperty("estimado") Double estimado,
        @JsonProperty("muestras_pedidas") int muestrasPedidas,
        @JsonProperty("muestras_ok") int muestrasOk,
        @JsonProperty("medido_en") Instant medidoEn,
        @JsonProperty("duracion_ms") long duracionMs
) {}
