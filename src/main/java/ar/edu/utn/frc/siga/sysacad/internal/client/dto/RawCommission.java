package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawCommission(
        @JsonProperty("curso") String curso,
        @JsonProperty("especialid") Integer especialid,
        @JsonProperty("plan") Integer plan,
        @JsonProperty("Materia") Integer materia,
        @JsonProperty("anoacademi") Integer anoacademi,
        @JsonProperty("Comision") Integer comision
) {}
