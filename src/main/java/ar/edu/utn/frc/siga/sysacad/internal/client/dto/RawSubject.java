package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawSubject(
        @JsonProperty("ESPECIALID") Integer especialid,
        @JsonProperty("plan") Integer plan,
        @JsonProperty("materia") Integer materia,
        @JsonProperty("MateriaNombre") String materiaNombre
) {}
