package ar.edu.utn.frc.siga.cameracount.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawHealth(
        @JsonProperty("estado") String estado,
        @JsonProperty("estimador") String estimador,
        @JsonProperty("version") String version
) {}
