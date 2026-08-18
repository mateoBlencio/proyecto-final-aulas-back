package ar.edu.utn.frc.siga.sysacad.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawBuilding(
        @JsonProperty("Edificio") Integer edificio,
        @JsonProperty("EdificioNombre") String edificioNombre
) {}
