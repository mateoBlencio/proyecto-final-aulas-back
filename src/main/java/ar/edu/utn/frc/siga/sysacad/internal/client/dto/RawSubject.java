package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Campos nombrados igual que en las vistas reales de SysAcad ya vistas: {@code especialid}/{@code plan}
 * como en Comisiones, {@code materia}/{@code MateriaNombre}/{@code MateriaDictado}/
 * {@code EspecialidadNombre} como en HorariosComisionesCupos. No existe (todavía) una vista "Materias"
 * real — ver SubjectMockViewFetcher.
 * <p>
 * {@code especialidadNombre} se conserva por fidelidad con la vista real, aunque {@link
 * ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper} no lo propaga: el nombre de la
 * especialidad ya llega por la vista real de Especialidades.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawSubject(
        @JsonProperty("especialid") Integer especialid,
        @JsonProperty("plan") Integer plan,
        @JsonProperty("materia") Integer materia,
        @JsonProperty("MateriaNombre") String materiaNombre,
        @JsonProperty("MateriaDictado") String materiaDictado,
        @JsonProperty("EspecialidadNombre") String especialidadNombre
) {}
