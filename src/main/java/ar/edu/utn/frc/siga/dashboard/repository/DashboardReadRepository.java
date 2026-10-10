package ar.edu.utn.frc.siga.dashboard.repository;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.PendingItemRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read model for the dashboard. Reads tables of allocation, events, space and roomrequest with
 * native SQL so it does not depend on those modules' entities.
 */
@Repository
@RequiredArgsConstructor
public class DashboardReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<BuildingRoomsRow> findBuildings(BuildingScope scope) {
        if (hasNoBuildings(scope)) {
            return List.of();
        }
        return jdbc.query("""
                SELECT b.id_edificio, b.codigo_edificio, b.nombre, COUNT(c.id_aula) AS room_count
                FROM edificio b
                LEFT JOIN aula c ON c.id_edificio = b.id_edificio AND c.eliminado_en IS NULL
                WHERE b.eliminado_en IS NULL AND (:unrestricted OR b.id_edificio IN (:buildingIds))
                GROUP BY b.id_edificio, b.codigo_edificio, b.nombre
                ORDER BY b.codigo_edificio
                """, scopeParams(scope),
                (rs, i) -> new BuildingRoomsRow(rs.getLong("id_edificio"), (Integer) rs.getObject("codigo_edificio"),
                        rs.getString("nombre"), rs.getInt("room_count")));
    }

    public List<AllocationUnitRow> findUnits(LocalDate from, LocalDate to, BuildingScope scope) {
        if (hasNoBuildings(scope)) {
            return List.of();
        }
        return jdbc.query("""
                SELECT a.id_asignacion, a.id_aula, c.id_edificio, a.origen, o.fecha,
                       e.id_evento_academico, e.hora_inicio, e.duracion_minutos, e.cantidad_inscriptos, c.capacidad
                FROM asignacion_aula a
                JOIN ocurrencia o ON o.id_ocurrencia = a.id_ocurrencia AND o.estado <> 'ROOM_RELEASED'
                JOIN evento_academico e ON e.id_evento_academico = o.id_evento_academico
                     AND NOT (e.hash_sysacad IS NOT NULL AND e.habilitado_sysacad = FALSE)
                JOIN aula c ON c.id_aula = a.id_aula AND c.eliminado_en IS NULL
                JOIN edificio b ON b.id_edificio = c.id_edificio AND b.eliminado_en IS NULL
                WHERE o.fecha BETWEEN :from AND :to
                  AND (:unrestricted OR b.id_edificio IN (:buildingIds))
                """, scopeParams(scope).addValue("from", from).addValue("to", to),
                (rs, i) -> toUnit(rs));
    }

    /** Classes without a room (not released, event not dropped from Sysacad) per date. */
    public Map<LocalDate, Long> countUnallocatedByDate(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> result = new HashMap<>();
        jdbc.query("""
                SELECT o.fecha, COUNT(*) AS total
                FROM ocurrencia o
                JOIN evento_academico e ON e.id_evento_academico = o.id_evento_academico
                     AND NOT (e.hash_sysacad IS NOT NULL AND e.habilitado_sysacad = FALSE)
                WHERE o.estado <> 'ROOM_RELEASED' AND o.fecha BETWEEN :from AND :to
                  AND NOT EXISTS (SELECT 1 FROM asignacion_aula a WHERE a.id_ocurrencia = o.id_ocurrencia)
                GROUP BY o.fecha
                """, new MapSqlParameterSource().addValue("from", from).addValue("to", to),
                (rs, i) -> result.put(rs.getObject("fecha", LocalDate.class), rs.getLong("total")));
        return result;
    }

    public List<PendingItemRow> findOpenItems() {
        return jdbc.query("""
                SELECT i.estado, i.id_edificio_derivado, i.fecha, i.hora_inicio, s.creado_en, i.fecha_derivacion
                FROM solicitud_aula_item i JOIN solicitud_aula s ON s.id_solicitud = i.id_solicitud
                WHERE i.estado IN ('NEW', 'DERIVED_TO_BUILDING', 'IN_EVALUATION')
                """, new MapSqlParameterSource(),
                (rs, i) -> new PendingItemRow(rs.getString("estado"), (Long) rs.getObject("id_edificio_derivado"),
                        rs.getObject("fecha", LocalDate.class), rs.getObject("hora_inicio", LocalTime.class),
                        rs.getObject("creado_en", LocalDateTime.class),
                        rs.getObject("fecha_derivacion", LocalDateTime.class)));
    }

    private static AllocationUnitRow toUnit(ResultSet rs) throws SQLException {
        int start = rs.getObject("hora_inicio", LocalTime.class).toSecondOfDay() / 60;
        return new AllocationUnitRow(rs.getLong("id_asignacion"), rs.getLong("id_aula"), rs.getLong("id_edificio"),
                rs.getString("origen"), rs.getObject("fecha", LocalDate.class), rs.getLong("id_evento_academico"),
                start, start + rs.getInt("duracion_minutos"), rs.getInt("cantidad_inscriptos"),
                (Integer) rs.getObject("capacidad"));
    }

    private static boolean hasNoBuildings(BuildingScope scope) {
        return !scope.isUnrestricted() && scope.buildingIds().isEmpty();
    }

    /** Scope parameters; the id list is never empty so the generated IN clause stays valid. */
    private static MapSqlParameterSource scopeParams(BuildingScope scope) {
        return new MapSqlParameterSource()
                .addValue("unrestricted", scope.isUnrestricted())
                .addValue("buildingIds", scope.isUnrestricted() ? List.of(-1L) : List.copyOf(scope.buildingIds()));
    }
}
