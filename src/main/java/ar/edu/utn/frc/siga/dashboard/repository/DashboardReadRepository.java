package ar.edu.utn.frc.siga.dashboard.repository;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.PendingItemRow;
import ar.edu.utn.frc.siga.dashboard.model.RequestCountRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final Clock clock;

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
                """, new MapSqlParameterSource().addValue("from", from).addValue("to", to),
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
                        toInstant(rs.getObject("creado_en", LocalDateTime.class)),
                        toInstant(rs.getObject("fecha_derivacion", OffsetDateTime.class))));
    }

    /**
     * Items resolved (RESOLVED) with notification in [from, to), per year. Per building of the allocated room
     * (an item with rooms in two buildings counts once in each) plus a campus row that counts it once.
     * The year is the one in the clock's zone.
     */
    public List<RequestCountRow> countResolved(Instant from, Instant to) {
        return jdbc.query("""
                SELECT t.year, t.building_id, GROUPING(t.building_id) AS is_campus,
                       COUNT(DISTINCT t.id_item) AS total
                FROM (SELECT EXTRACT(YEAR FROM i.fecha_notificacion AT TIME ZONE :zone)::int AS year,
                             c.id_edificio AS building_id, i.id_item
                      FROM solicitud_aula_item i
                      LEFT JOIN solicitud_item_asignacion ia ON ia.id_item = i.id_item
                      LEFT JOIN aula c ON c.id_aula = ia.id_aula
                      WHERE i.estado = 'RESOLVED' AND i.fecha_notificacion >= :from AND i.fecha_notificacion < :to) t
                GROUP BY GROUPING SETS ((t.year), (t.year, t.building_id))
                """, rangeParams(from, to),
                (rs, i) -> new RequestCountRow(rs.getInt("year"), (Long) rs.getObject("building_id"),
                        rs.getInt("is_campus") == 1, rs.getLong("total")))
                .stream()
                .filter(row -> row.campus() || row.buildingId() != null)
                .toList();
    }

    /** Items cancelled by the expiry process (decidido_por = SISTEMA) with decision in [from, to), per year and derived building. */
    public List<RequestCountRow> countExpired(Instant from, Instant to) {
        return jdbc.query("""
                SELECT EXTRACT(YEAR FROM i.fecha_decision AT TIME ZONE :zone)::int AS year,
                       i.id_edificio_derivado AS building_id, COUNT(*) AS total
                FROM solicitud_aula_item i
                WHERE i.estado = 'CANCELLED' AND i.decidido_por = 'SISTEMA'
                  AND i.fecha_decision >= :from AND i.fecha_decision < :to
                GROUP BY 1, 2
                """, rangeParams(from, to),
                (rs, i) -> new RequestCountRow(rs.getInt("year"), (Long) rs.getObject("building_id"),
                        false, rs.getLong("total")));
    }

    /**
     * Items that were open at {@code at}, per derived building (current derived building). Rebuilt from the
     * current state: created before {@code at} and not resolved (notified) or cancelled before it.
     * {@code creado_en} has no zone, so it is compared in the clock's zone.
     */
    public List<RequestCountRow> countPendingAt(Instant at) {
        return jdbc.query("""
                SELECT i.id_edificio_derivado AS building_id, COUNT(*) AS total
                FROM solicitud_aula_item i JOIN solicitud_aula s ON s.id_solicitud = i.id_solicitud
                WHERE s.creado_en < :atLocal
                  AND NOT (i.estado = 'RESOLVED'  AND COALESCE(i.fecha_notificacion < :at, TRUE))
                  AND NOT (i.estado = 'CANCELLED' AND COALESCE(i.fecha_decision    < :at, TRUE))
                GROUP BY 1
                """, new MapSqlParameterSource().addValue("at", utc(at))
                        .addValue("atLocal", LocalDateTime.ofInstant(at, clock.getZone())),
                (rs, i) -> new RequestCountRow(0, (Long) rs.getObject("building_id"), false, rs.getLong("total")));
    }

    private MapSqlParameterSource rangeParams(Instant from, Instant to) {
        return new MapSqlParameterSource().addValue("zone", clock.getZone().getId())
                .addValue("from", utc(from)).addValue("to", utc(to));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(clock.getZone()).toInstant();
    }

    private static Instant toInstant(OffsetDateTime offset) {
        return offset == null ? null : offset.toInstant();
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
