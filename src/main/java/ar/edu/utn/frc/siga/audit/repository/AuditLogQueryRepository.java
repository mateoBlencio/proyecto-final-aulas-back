package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.RevisionType;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Audit log read using aggregate SQL over {@code revinfo} and the {@code _aud} tables.
 * Only table/column names coming from {@link AuditedEntityRegistry} and branch indexes are
 * integer concatenated into the SQL; every request value travels as a parameter.
 */
@Repository
@RequiredArgsConstructor
public class AuditLogQueryRepository {

    // Envers columns with the default naming (see V1 and V7).
    private static final String REV = "rev";
    private static final String REVTYPE = "revtype";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditedEntityRegistry registry;

    public long countGroups(AuditLogCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM (" + groupsQuery(criteria, params, "SELECT 1") + ") g";
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    public List<AuditGroupRow> findGroups(AuditLogCriteria criteria, Pageable pageable) {
        // Phase 1: page of groups, over revinfo only.
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = groupsQuery(criteria, params, "SELECT r.operacion_id AS operation_id, MAX(r." + REV + ") AS revision")
                + " ORDER BY revision DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<String> operationIds = new ArrayList<>();
        List<Integer> revisions = new ArrayList<>();
        jdbc.query(sql, params, rs -> {
            String operationId = rs.getString("operation_id");
            if (operationId != null) {
                operationIds.add(operationId);
            } else {
                revisions.add(rs.getInt("revision"));
            }
        });
        if (operationIds.isEmpty() && revisions.isEmpty()) {
            return List.of();
        }

        // Phase 2: aggregates for the groups on the page.
        MapSqlParameterSource aggParams = new MapSqlParameterSource();
        List<String> restrictions = new ArrayList<>();
        if (!operationIds.isEmpty()) {
            restrictions.add("r.operacion_id IN (:operationIds)");
            aggParams.addValue("operationIds", operationIds);
        }
        if (!revisions.isEmpty()) {
            restrictions.add("(r.operacion_id IS NULL AND r." + REV + " IN (:revisions))");
            aggParams.addValue("revisions", revisions);
        }
        String union = unionBranches(criteria, aggParams, "(" + String.join(" OR ", restrictions) + ")");
        String aggSql = "SELECT c.operacion_id, MAX(c.rev) AS revision, MAX(c.fecha_revision) AS fecha, "
                + "MAX(c.usuario) AS usuario, MAX(c.descripcion) AS descripcion, COUNT(*) AS record_count, "
                + "MIN(c.revtype) AS min_revtype, MAX(c.revtype) AS max_revtype, "
                + "MIN(c.entity_idx) AS min_entity_idx, MIN(c.record_id) AS min_record_id, "
                + "string_agg(DISTINCT CAST(c.entity_idx AS varchar), ',') AS entity_idxs "
                + "FROM (" + union + ") c "
                + "GROUP BY c.operacion_id, CASE WHEN c.operacion_id IS NULL THEN c.rev END "
                + "ORDER BY revision DESC";

        List<AuditedEntity> entities = registry.all();
        return jdbc.query(aggSql, aggParams, (rs, rowNum) -> {
            List<String> entityTypes = Arrays.stream(rs.getString("entity_idxs").split(","))
                    .map(idx -> entities.get(Integer.parseInt(idx)).label())
                    .sorted()
                    .toList();
            int minRevtype = rs.getInt("min_revtype");
            RevisionKind commonKind = minRevtype == rs.getInt("max_revtype") ? toKind(minRevtype) : null;
            return new AuditGroupRow(
                    rs.getString("operacion_id"),
                    rs.getInt("revision"),
                    rs.getObject("fecha", LocalDateTime.class),
                    rs.getString("usuario"),
                    rs.getString("descripcion"),
                    rs.getLong("record_count"),
                    entityTypes,
                    commonKind,
                    entities.get(rs.getInt("min_entity_idx")).label(),
                    rs.getString("min_record_id"));
        });
    }

    public long countChanges(AuditLogCriteria criteria, ChangeScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM (" + unionBranches(criteria, params, scopeRestriction(scope, params)) + ") c";
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    public List<AuditChangeRow> findChanges(AuditLogCriteria criteria, ChangeScope scope, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT c.* FROM (" + unionBranches(criteria, params, scopeRestriction(scope, params)) + ") c "
                + "ORDER BY c.rev DESC, c.entity_idx, c.record_id LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<AuditedEntity> entities = registry.all();
        return jdbc.query(sql, params, (rs, rowNum) -> new AuditChangeRow(
                new RevisionMetadata(
                        rs.getString("record_id"),
                        rs.getInt("rev"),
                        rs.getObject("fecha_revision", LocalDateTime.class),
                        rs.getString("usuario"),
                        toKind(rs.getInt("revtype")),
                        rs.getString("descripcion"),
                        rs.getString("operacion_id")),
                entities.get(rs.getInt("entity_idx")).label()));
    }

    /**
     * Groups revisions: one per operation, or one per standalone revision. Restricts to revisions with rows
     * in the target tables with an IN over a UNION ALL: with an OR of correlated EXISTS, Postgres
     * scanned the whole asignacion_aula_aud table for each revision (39 s vs 73 ms in dev with ~416k _aud rows).
     */
    private String groupsQuery(AuditLogCriteria criteria, MapSqlParameterSource params, String select) {
        StringBuilder where = new StringBuilder(revisionFilters(criteria, params));
        String revisionsWithRows = criteria.targets().stream()
                .map(target -> "SELECT x." + REV + " FROM " + target.auditTable() + " x WHERE TRUE"
                        + rowFilter(criteria, params))
                .collect(Collectors.joining(" UNION ALL "));
        where.append(" AND r.").append(REV).append(" IN (").append(revisionsWithRows).append(")");
        return select + " FROM revinfo r WHERE " + where
                + " GROUP BY r.operacion_id, CASE WHEN r.operacion_id IS NULL THEN r." + REV + " END";
    }

    private String unionBranches(AuditLogCriteria criteria, MapSqlParameterSource params, String extraRestriction) {
        String revFilters = revisionFilters(criteria, params);
        return criteria.targets().stream()
                .map(target -> "SELECT r." + REV + ", r.fecha_revision, r.usuario, r.descripcion, r.operacion_id, "
                        + "x." + REVTYPE + ", CAST(x." + target.idColumn() + " AS varchar) AS record_id, "
                        + registry.indexOf(target) + " AS entity_idx "
                        + "FROM revinfo r JOIN " + target.auditTable() + " x ON x." + REV + " = r." + REV
                        + " WHERE " + revFilters + rowFilter(criteria, params) + " AND " + extraRestriction)
                .collect(Collectors.joining(" UNION ALL "));
    }

    private static String scopeRestriction(ChangeScope scope, MapSqlParameterSource params) {
        if (scope.operationId() != null) {
            params.addValue("scopeOperationId", scope.operationId());
            return "r.operacion_id = :scopeOperationId";
        }
        params.addValue("scopeRevision", scope.revision());
        return "r." + REV + " = :scopeRevision";
    }

    private static String revisionFilters(AuditLogCriteria criteria, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("TRUE");
        if (criteria.from() != null) {
            sql.append(" AND r.fecha_revision >= :from");
            params.addValue("from", criteria.from());
        }
        if (criteria.toExclusive() != null) {
            sql.append(" AND r.fecha_revision < :toExclusive");
            params.addValue("toExclusive", criteria.toExclusive());
        }
        if (criteria.user() != null && !criteria.user().isBlank()) {
            sql.append(" AND r.usuario ILIKE :userPattern ESCAPE '\\'");
            params.addValue("userPattern", "%" + escapeLike(criteria.user()) + "%");
        }
        return sql.toString();
    }

    private static String rowFilter(AuditLogCriteria criteria, MapSqlParameterSource params) {
        if (criteria.kind() == null) {
            return "";
        }
        params.addValue("revtype", toType(criteria.kind()).getRepresentation());
        return " AND x." + REVTYPE + " = :revtype";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static RevisionType toType(RevisionKind kind) {
        return switch (kind) {
            case CREATED -> RevisionType.ADD;
            case MODIFIED -> RevisionType.MOD;
            case DELETED -> RevisionType.DEL;
        };
    }

    private static RevisionKind toKind(int revtype) {
        return switch (RevisionType.fromRepresentation((byte) revtype)) {
            case ADD -> RevisionKind.CREATED;
            case MOD -> RevisionKind.MODIFIED;
            case DEL -> RevisionKind.DELETED;
        };
    }
}
