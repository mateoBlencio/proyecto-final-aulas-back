package ar.edu.utn.frc.siga.sysacad.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawClassroom(
        @JsonProperty("Aula") Integer aula,
        @JsonProperty("Edificio") Integer edificio,
        @JsonProperty("habilitada") String habilitada,
        @JsonProperty("capacidad") Integer capacidad
) {}
