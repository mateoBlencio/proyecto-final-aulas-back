package ar.edu.utn.frc.siga.audit.repository;

import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditArchiveExportRepository {

    private static final String IN_RANGE = """
            r.fecha_revision < :to
            AND (CAST(:from AS timestamp) IS NULL OR r.fecha_revision >= CAST(:from AS timestamp))""";

    private final NamedParameterJdbcTemplate jdbc;

    public long count(ArchivedTable table, LocalDate from, LocalDate to) {
        String sql = "SELECT count(*) FROM archivo." + table.table() + " a JOIN archivo.revinfo r ON r.rev = a.rev WHERE "
                + IN_RANGE;
        Long total = jdbc.queryForObject(sql, range(from, to), Long.class);
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> readPage(ArchivedTable table, LocalDate from, LocalDate to,
                                              long afterRev, long afterId, int limit) {
        String id = table.idColumn();
        String sql = "SELECT r.fecha_revision, r.usuario, r.tipo_actor, r.descripcion, r.operacion_id, a.*"
                + " FROM archivo." + table.table() + " a JOIN archivo.revinfo r ON r.rev = a.rev WHERE " + IN_RANGE
                + " AND (a.rev, a." + id + ") > (:afterRev, :afterId)"
                + " ORDER BY a.rev, a." + id + " LIMIT :limit";
        MapSqlParameterSource params = range(from, to)
                .addValue("afterRev", afterRev)
                .addValue("afterId", afterId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, rowNum) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 1; column <= meta.getColumnCount(); column++) {
                row.put(meta.getColumnLabel(column), rs.getObject(column));
            }
            return row;
        });
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", from == null ? null : java.sql.Timestamp.valueOf(from.atStartOfDay()))
                .addValue("to", java.sql.Timestamp.valueOf(to.atStartOfDay()));
    }
}
