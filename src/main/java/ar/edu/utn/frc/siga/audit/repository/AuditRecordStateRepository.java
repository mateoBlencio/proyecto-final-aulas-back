package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads the audited state of a record at a revision and at its previous revision. One query per entity.
 * The current row is found through the primary key; the previous-row lookup needs an {@code (id, rev)}
 * index, which the {@code _aud} tables with a {@code (rev, id)} primary key get from migration V15. Table and
 * column names come from {@link AuditedEntity}; ids and revisions travel as parameters.
 */
@Repository
@RequiredArgsConstructor
public class AuditRecordStateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Current and previous state of each requested record revision. Keys with no audit row are absent. */
    public Map<RecordRevision, RecordStates> load(Collection<RecordRevision> keys) {
        Map<AuditedEntity, List<RecordRevision>> byEntity = keys.stream()
                .collect(Collectors.groupingBy(RecordRevision::entity, LinkedHashMap::new, Collectors.toList()));
        Map<RecordRevision, RecordStates> result = new HashMap<>();
        byEntity.forEach((entity, entityKeys) -> loadEntity(entity, entityKeys, result));
        return result;
    }

    private void loadEntity(AuditedEntity entity, List<RecordRevision> keys, Map<RecordRevision, RecordStates> result) {
        List<AuditedColumn> columns = entity.columns();
        List<String> subTables = columns.stream().map(AuditedColumn::auditTable)
                .filter(table -> !table.equals(entity.auditTable()))
                .distinct().toList();

        String id = entity.idColumn();
        StringBuilder select = new StringBuilder("SELECT CAST(cur." + id + " AS varchar) AS record_id, cur.rev AS rev");
        StringBuilder from = new StringBuilder(" FROM " + entity.auditTable() + " cur"
                + " LEFT JOIN LATERAL (SELECT p.rev FROM " + entity.auditTable() + " p"
                + " WHERE p." + id + " = cur." + id + " AND p.rev < cur.rev ORDER BY p.rev DESC LIMIT 1) pk ON TRUE"
                + " LEFT JOIN " + entity.auditTable() + " prev ON prev." + id + " = cur." + id + " AND prev.rev = pk.rev");
        for (int i = 0; i < subTables.size(); i++) {
            from.append(" LEFT JOIN ").append(subTables.get(i)).append(" cur_s").append(i)
                    .append(" ON cur_s").append(i).append(".").append(id).append(" = cur.").append(id)
                    .append(" AND cur_s").append(i).append(".rev = cur.rev")
                    .append(" LEFT JOIN ").append(subTables.get(i)).append(" prev_s").append(i)
                    .append(" ON prev_s").append(i).append(".").append(id).append(" = prev.").append(id)
                    .append(" AND prev_s").append(i).append(".rev = prev.rev");
        }
        for (int i = 0; i < columns.size(); i++) {
            AuditedColumn column = columns.get(i);
            int subIndex = subTables.indexOf(column.auditTable());
            String curAlias = subIndex < 0 ? "cur" : "cur_s" + subIndex;
            String prevAlias = subIndex < 0 ? "prev" : "prev_s" + subIndex;
            select.append(", ").append(curAlias).append(".").append(column.column()).append(" AS c").append(i)
                    .append(", ").append(prevAlias).append(".").append(column.column()).append(" AS p").append(i);
        }

        Map<String, RecordRevision> keyByRecord = new HashMap<>();
        List<Object[]> idsAndRevisions = new ArrayList<>();
        for (RecordRevision key : keys) {
            keyByRecord.put(key.recordId() + "@" + key.revision(), key);
            idsAndRevisions.add(new Object[]{convertId(key.recordId(), entity.idType()), key.revision()});
        }
        String sql = select.append(from).append(" WHERE (cur." + id + ", cur.rev) IN (:keys)").toString();

        jdbc.query(sql, new MapSqlParameterSource("keys", idsAndRevisions), rs -> {
            Map<String, Object> current = new LinkedHashMap<>();
            Map<String, Object> previous = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                current.put(columns.get(i).property(), normalize(rs.getObject("c" + i)));
                previous.put(columns.get(i).property(), normalize(rs.getObject("p" + i)));
            }
            RecordRevision key = keyByRecord.get(rs.getString("record_id") + "@" + rs.getInt("rev"));
            result.put(key, new RecordStates(current, previous));
        });
    }

    private static Object convertId(String recordId, Class<?> idType) {
        if (idType == Long.class) {
            return Long.valueOf(recordId);
        }
        if (idType == Integer.class) {
            return Integer.valueOf(recordId);
        }
        if (idType == UUID.class) {
            return UUID.fromString(recordId);
        }
        if (idType == String.class) {
            return recordId;
        }
        throw new IllegalStateException("Tipo de identificador no soportado: " + idType.getName());
    }

    private static Object normalize(Object value) {
        return switch (value) {
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            case Date date -> date.toLocalDate();
            case Time time -> time.toLocalTime();
            case null, default -> value;
        };
    }

    public record RecordRevision(AuditedEntity entity, String recordId, int revision) {
    }

    /**
     * Audited values by property name. {@code previous} has every property null when the record has no
     * earlier audit row.
     */
    public record RecordStates(Map<String, Object> current, Map<String, Object> previous) {
    }
}
