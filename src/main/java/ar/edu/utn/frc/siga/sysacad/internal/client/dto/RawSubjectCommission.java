package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Campos nombrados igual que en HorariosComisionesCupos (única vista real donde vimos inscriptos por
 * materia dentro de una comisión). No existe una vista real para esto tampoco — ver
 * SubjectCommissionMockViewFetcher.
 * <p>
 * {@code comisionDictado} se conserva por fidelidad con la vista real (no está en la vista Comisiones y,
 * a diferencia de {@code EspecialidadNombre} en RawSubject, no es redundante con nada que ya
 * sincronicemos) pero {@link ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper} todavía no
 * lo propaga — no hay columna en {@code materia_comision} para guardarlo. Solo está disponible para los
 * pares (curso, materia) que vienen de HorariosComisionesCupos; el CSV no tiene este dato, así que en la
 * mayoría de las filas del fixture queda {@code null}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawSubjectCommission(
        @JsonProperty("Curso") String curso,
        @JsonProperty("materia") Integer materia,
        @JsonProperty("Inscriptos") Integer inscriptos,
        @JsonProperty("ComisionDictado") String comisionDictado
) {}
