package ar.edu.utn.frc.siga.allocation.repository;

import ar.edu.utn.frc.siga.allocation.model.AllocationSearchRow;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AllocationSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<AllocationSearchRow> search(String pattern, LocalDate date, LocalDate from, LocalDate to,
                                            int limit, BuildingScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", pattern)
                .addValue("date", date)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("limit", limit)
                .addValue("unrestricted", scope.isUnrestricted())
                .addValue("buildingIds", scope.isUnrestricted() ? List.of(-1L) : List.copyOf(scope.buildingIds()));
        return jdbc.query("""
                WITH matched AS (
                    SELECT a.id_asignacion, o.id_ocurrencia, o.fecha, e.id_evento_academico, e.tipo_evento,
                           eu.tipo_actividad, e.hora_inicio, e.duracion_minutos, m.nombre AS materia,
                           co.codigo_curso, es.nombre AS especialidad, c.id_aula, c.numero, c.id_edificio
                    FROM asignacion_aula a
                    JOIN ocurrencia o ON o.id_ocurrencia = a.id_ocurrencia AND o.estado <> 'ROOM_RELEASED'
                    JOIN evento_academico e ON e.id_evento_academico = o.id_evento_academico
                    LEFT JOIN evento_unico eu ON eu.id_evento_academico = e.id_evento_academico
                    JOIN aula c ON c.id_aula = a.id_aula
                    LEFT JOIN materia m ON m.id_materia = e.id_materia
                    LEFT JOIN plan_estudio p ON p.id_plan = m.id_plan
                    LEFT JOIN especialidad es ON es.id_especialidad = p.id_especialidad
                    LEFT JOIN comision co ON co.id_comision = e.id_comision
                    WHERE o.fecha BETWEEN :from AND :to
                      AND (:unrestricted OR c.id_edificio IN (:buildingIds))
                      AND (public.unaccent(lower(coalesce(m.nombre, ''))) LIKE public.unaccent(lower(:pattern)) ESCAPE '\\'
                        OR public.unaccent(lower(coalesce(co.codigo_curso, ''))) LIKE public.unaccent(lower(:pattern)) ESCAPE '\\'
                        OR CAST(c.numero AS text) LIKE public.unaccent(lower(:pattern)) ESCAPE '\\'
                        OR public.unaccent(lower(coalesce(es.nombre, ''))) LIKE public.unaccent(lower(:pattern)) ESCAPE '\\')
                ), events AS (
                    SELECT id_evento_academico,
                           min(fecha) FILTER (WHERE fecha >= :date) AS next_date,
                           bool_and(fecha < :date) AS finished,
                           max(fecha) AS last_date,
                           min(materia) AS materia
                    FROM matched
                    GROUP BY id_evento_academico
                    ORDER BY finished, next_date, last_date DESC, materia, id_evento_academico
                    LIMIT :limit
                )
                SELECT mt.* FROM matched mt JOIN events ev USING (id_evento_academico)
                ORDER BY ev.finished, ev.next_date, ev.last_date DESC, ev.materia, ev.id_evento_academico,
                         mt.fecha, mt.hora_inicio, mt.id_asignacion
                """, params, (rs, i) -> {
                    String activityType = rs.getString("tipo_actividad");
                    return new AllocationSearchRow(
                            rs.getLong("id_asignacion"), rs.getLong("id_ocurrencia"),
                            rs.getObject("fecha", LocalDate.class), rs.getLong("id_evento_academico"),
                            EventType.valueOf(rs.getString("tipo_evento")),
                            activityType == null ? null : UniqueEventKind.valueOf(activityType),
                            rs.getObject("hora_inicio", LocalTime.class), rs.getInt("duracion_minutos"),
                            rs.getString("materia"), rs.getString("codigo_curso"), rs.getString("especialidad"),
                            rs.getLong("id_aula"), rs.getInt("numero"), rs.getLong("id_edificio"));
                });
    }
}
