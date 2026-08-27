package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawSchedule(
        @JsonProperty("Curso") String curso,
        @JsonProperty("Comision") Integer comision,
        @JsonProperty("Aula") Integer aula,
        @JsonProperty("Edificio") Integer edificio,
        @JsonProperty("EdificioNombre") String edificioNombre,
        @JsonProperty("Dia") Integer dia,
        @JsonProperty("HorarioCuatrimestre") Integer horarioCuatrimestre,
        @JsonProperty("ComisionDictado") String comisionDictado,
        @JsonProperty("MateriaDictado") String materiaDictado,
        @JsonProperty("HoraComienzo") String horaComienzo,
        @JsonProperty("HoraFin") String horaFin,
        @JsonProperty("RangoHorario") String rangoHorario,
        @JsonProperty("DURACION") Integer duracion,
        @JsonProperty("Especialidad") Integer especialidad,
        @JsonProperty("EspecialidadNombre") String especialidadNombre,
        @JsonProperty("Plan") Integer plan,
        @JsonProperty("materia") Integer materia,
        @JsonProperty("MateriaNombre") String materiaNombre,
        @JsonProperty("Inscriptos") Integer inscriptos
) {}
