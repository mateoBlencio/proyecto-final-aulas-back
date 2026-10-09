package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.model.ActorType;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Audit log read using aggregate SQL over {@code revinfo}, {@code revinfo_resumen} and the {@code _aud} tables.
 * Only table/column names coming from {@link AuditedEntityRegistry} and branch indexes are
 * integer concatenated into the SQL; every request value travels as a parameter.
 */
@Repository
@RequiredArgsConstructor
public class AuditLogQueryRepository {

    // Envers columns with the default naming (see V1 and V7).
    private static final String REV = "rev";
    private static final String REVTYPE = "revtype";
    private static final int MAX_CHAIN_DEPTH = 10;
    private static final int MAX_CHAIN_OPERATIONS = 200;

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

        return aggregateGroups(criteria, operationIds, revisions);
    }

    /**
     * Operations that form the causal chain of {@code operationId}: its ancestors up to the root and all
     * the descendants of that root (siblings and their children included), up to 10 levels in each direction.
     * Capped at {@value #MAX_CHAIN_OPERATIONS} operations selected by revision ascending (the cause first); the returned rows keep the listing order (revision descending), so callers sort them.
     * {@code truncated} is true if the cap cut operations or if the 10-level limit stopped the walk before
     * the real root (the one with a null parent) or before the leaves.
     * Filters are not applied: the chain shows the whole cause and effect. Empty if no revision belongs to the id.
     * Each operation is its own group, so the actor of an entry never mixes humans and systems; a child
     * operation of a human one (listener thread) shows as SYSTEM in its own entry.
     */
    public AuditChainRows findOperationChain(String operationId) {
        String sql = "WITH RECURSIVE ancestors(op, depth) AS ("
                + "SELECT CAST(:id AS varchar), 0 "
                + "UNION "
                + "SELECT r.operacion_padre_id, a.depth + 1 FROM ancestors a "
                + "JOIN revinfo r ON r.operacion_id = a.op "
                + "WHERE r.operacion_padre_id IS NOT NULL AND a.depth < " + MAX_CHAIN_DEPTH + "), "
                + "root(op) AS (SELECT op FROM ancestors ORDER BY depth DESC LIMIT 1), "
                + "descendants(op, depth) AS ("
                + "SELECT op, 0 FROM root "
                + "UNION "
                + "SELECT r.operacion_id, d.depth + 1 FROM descendants d "
                + "JOIN revinfo r ON r.operacion_padre_id = d.op "
                + "WHERE d.depth < " + MAX_CHAIN_DEPTH + "), "
                + "ordered AS (SELECT r.operacion_id AS op, MAX(r." + REV + ") AS revision FROM revinfo r "
                + "WHERE r.operacion_id IN (SELECT op FROM descendants) "
                + "GROUP BY r.operacion_id ORDER BY revision, r.operacion_id LIMIT " + (MAX_CHAIN_OPERATIONS + 1) + ") "
                + "SELECT o.op, (EXISTS (SELECT 1 FROM revinfo p WHERE p.operacion_id = (SELECT op FROM root) "
                + "AND p.operacion_padre_id IS NOT NULL) "
                + "OR EXISTS (SELECT 1 FROM descendants d JOIN revinfo c ON c.operacion_padre_id = d.op "
                + "WHERE d.depth = " + MAX_CHAIN_DEPTH + ")) AS depth_cut "
                + "FROM ordered o ORDER BY o.revision, o.op";
        List<String> operationIds = new ArrayList<>();
        boolean[] depthCut = {false};
        jdbc.query(sql, new MapSqlParameterSource("id", operationId), rs -> {
            operationIds.add(rs.getString("op"));
            depthCut[0] = rs.getBoolean("depth_cut");
        });
        if (operationIds.isEmpty()) {
            return new AuditChainRows(List.of(), false);
        }
        boolean truncated = depthCut[0];
        if (operationIds.size() > MAX_CHAIN_OPERATIONS) {
            operationIds.remove(operationIds.size() - 1);
            truncated = true;
        }
        AuditLogCriteria unfiltered = new AuditLogCriteria(null, null, null, null, registry.all(), null, null);
        return new AuditChainRows(aggregateGroups(unfiltered, operationIds, List.of()), truncated);
    }

    /**
     * Aggregates for the groups on the page. {@code MAX(tipo_actor)} per group: an operation lives in one
     * thread, so its revisions share the actor; a parent and a child are different groups.
     */
    private List<AuditGroupRow> aggregateGroups(AuditLogCriteria criteria, List<String> operationIds, List<Integer> revisions) {
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
        String aggSql = "SELECT r.operacion_id, MAX(r." + REV + ") AS revision, MAX(r.fecha_revision) AS fecha, "
                + "MAX(r.usuario) AS usuario, MAX(r.tipo_actor) AS tipo_actor, MAX(r.descripcion) AS descripcion, MAX(r.operacion_padre_id) AS operacion_padre_id, "
                + "SUM(s.cantidad) AS record_count, MIN(s.revtype) AS min_revtype, MAX(s.revtype) AS max_revtype, "
                + "string_agg(DISTINCT s.tabla_aud, ',') AS audit_tables "
                + "FROM revinfo r JOIN revinfo_resumen s ON s.rev = r.rev "
                + "WHERE " + revisionFilters(criteria, aggParams) + " AND (" + String.join(" OR ", restrictions) + ")"
                + summaryFilter(criteria, aggParams)
                + " GROUP BY r.operacion_id, CASE WHEN r.operacion_id IS NULL THEN r." + REV + " END "
                + "ORDER BY revision DESC";

        List<AuditedEntity> entities = registry.all();
        record Group(AuditGroupRow row, List<AuditedEntity> tables) {
        }
        List<Group> groups = jdbc.query(aggSql, aggParams, (rs, rowNum) -> {
            List<AuditedEntity> tables = Arrays.stream(rs.getString("audit_tables").split(","))
                    .map(this::entityOf)
                    .sorted(Comparator.comparingInt(registry::indexOf))
                    .toList();
            int minRevtype = rs.getInt("min_revtype");
            RevisionKind commonKind = minRevtype == rs.getInt("max_revtype") ? toKind(minRevtype) : null;
            return new Group(new AuditGroupRow(
                    rs.getString("operacion_id"),
                    rs.getString("operacion_padre_id"),
                    rs.getInt("revision"),
                    rs.getObject("fecha", LocalDateTime.class),
                    rs.getString("usuario"),
                    ActorType.valueOf(rs.getString("tipo_actor")),
                    rs.getString("descripcion"),
                    rs.getLong("record_count"),
                    tables.stream().map(AuditedEntity::label).sorted().toList(),
                    commonKind,
                    tables.getFirst().label(),
                    null), tables);
        });

        Map<String, String> singleIds = singleRecordIds(criteria, groups.stream()
                .filter(g -> g.row().recordCount() == 1)
                .map(g -> new SingleGroup(g.row().operationId(), g.row().revision(), g.tables().getFirst()))
                .toList());
        return groups.stream().map(g -> {
            AuditGroupRow row = g.row();
            String recordId = row.recordCount() == 1 ? singleIds.get(groupKey(row.operationId(), row.revision())) : null;
            return new AuditGroupRow(row.operationId(), row.parentOperationId(), row.revision(), row.date(), row.user(),
                    row.actorType(), row.description(), row.recordCount(), row.entityTypes(), row.commonKind(),
                    row.singleEntityType(), recordId);
        }).toList();
    }

    private record SingleGroup(String operationId, int revision, AuditedEntity table) {
    }

    private static String groupKey(String operationId, int revision) {
        return operationId != null ? "op:" + operationId : "rev:" + revision;
    }

    /**
     * Record id of the groups with exactly one row, read from the {@code _aud} table of that row
     * (index {@code idx_*_aud_rev}). One query per table, restricted to the revisions of the page.
     */
    private Map<String, String> singleRecordIds(AuditLogCriteria criteria, List<SingleGroup> singles) {
        Map<String, String> ids = new HashMap<>();
        singles.stream().collect(Collectors.groupingBy(SingleGroup::table)).forEach((table, groups) -> {
            MapSqlParameterSource params = new MapSqlParameterSource();
            List<String> operationIds = groups.stream().map(SingleGroup::operationId).filter(Objects::nonNull).toList();
            List<Integer> revisions = groups.stream().filter(g -> g.operationId() == null).map(SingleGroup::revision).toList();
            List<String> restrictions = new ArrayList<>();
            if (!operationIds.isEmpty()) {
                restrictions.add("r.operacion_id IN (:operationIds)");
                params.addValue("operationIds", operationIds);
            }
            if (!revisions.isEmpty()) {
                restrictions.add("(r.operacion_id IS NULL AND r." + REV + " IN (:revisions))");
                params.addValue("revisions", revisions);
            }
            String sql = "SELECT r.operacion_id, r." + REV + ", CAST(x." + table.idColumn() + " AS varchar) AS record_id "
                    + "FROM " + table.auditTable() + " x JOIN revinfo r ON r." + REV + " = x." + REV
                    + " WHERE (" + String.join(" OR ", restrictions) + ")" + rowFilter(criteria, params);
            jdbc.query(sql, params, rs -> {
                ids.put(groupKey(rs.getString("operacion_id"), rs.getInt(REV)), rs.getString("record_id"));
            });
        });
        return ids;
    }

    private AuditedEntity entityOf(String auditTable) {
        return registry.all().stream().filter(e -> e.auditTable().equals(auditTable)).findFirst().orElseThrow();
    }

    public long countChanges(AuditLogCriteria criteria, ChangeScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM (" + unionBranches(criteria, params, target -> scopeRestriction(scope, target, params)) + ") c";
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    public List<AuditChangeRow> findChanges(AuditLogCriteria criteria, ChangeScope scope, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT c.* FROM (" + unionBranches(criteria, params, target -> scopeRestriction(scope, target, params)) + ") c "
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
                        ActorType.valueOf(rs.getString("tipo_actor")),
                        toKind(rs.getInt("revtype")),
                        rs.getString("descripcion"),
                        rs.getString("operacion_id")),
                entities.get(rs.getInt("entity_idx"))));
    }

    /**
     * Groups revisions: one per operation, or one per standalone revision. Restricts to revisions with rows
     * in the target tables via {@code revinfo_resumen} (one row per revision, table and type, fed on write),
     * instead of a UNION over every {@code _aud} table (~416k index entries in dev).
     */
    private String groupsQuery(AuditLogCriteria criteria, MapSqlParameterSource params, String select) {
        String where = revisionFilters(criteria, params)
                + " AND r." + REV + " IN (SELECT s.rev FROM revinfo_resumen s WHERE TRUE" + summaryFilter(criteria, params) + ")";
        return select + " FROM revinfo r WHERE " + where
                + " GROUP BY r.operacion_id, CASE WHEN r.operacion_id IS NULL THEN r." + REV + " END";
    }

    /** Restricts a {@code revinfo_resumen s} to the target tables and the {@code kind} filter. */
    private static String summaryFilter(AuditLogCriteria criteria, MapSqlParameterSource params) {
        params.addValue("summaryTables", criteria.targets().stream().map(AuditedEntity::auditTable).toList());
        String sql = " AND s.tabla_aud IN (:summaryTables)";
        if (criteria.kind() != null) {
            params.addValue("summaryRevtype", toType(criteria.kind()).getRepresentation());
            sql += " AND s.revtype = :summaryRevtype";
        }
        return sql;
    }

    private String unionBranches(AuditLogCriteria criteria, MapSqlParameterSource params,
                                Function<AuditedEntity, String> extraRestriction) {
        String revFilters = revisionFilters(criteria, params);
        return criteria.targets().stream()
                .map(target -> "SELECT r." + REV + ", r.fecha_revision, r.usuario, r.tipo_actor, r.descripcion, r.operacion_id, r.operacion_padre_id, "
                        + "x." + REVTYPE + ", CAST(x." + target.idColumn() + " AS varchar) AS record_id, "
                        + registry.indexOf(target) + " AS entity_idx "
                        + "FROM revinfo r JOIN " + target.auditTable() + " x ON x." + REV + " = r." + REV
                        + " WHERE " + revFilters + rowFilter(criteria, params) + " AND " + extraRestriction.apply(target))
                .collect(Collectors.joining(" UNION ALL "));
    }

    private static String scopeRestriction(ChangeScope scope, AuditedEntity target, MapSqlParameterSource params) {
        if (scope.operationId() != null) {
            params.addValue("scopeOperationId", scope.operationId());
            return "r.operacion_id = :scopeOperationId";
        }
        if (scope.entity() != null) {
            // Only the branch of the scoped entity can match; the others use their own id column, which may not exist here.
            if (!target.equals(scope.entity())) {
                return "FALSE";
            }
            params.addValue("scopeRecordId", scope.recordId());
            return "x." + target.idColumn() + " = :scopeRecordId";
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
        if (criteria.actor() != null) {
            sql.append(" AND r.tipo_actor = :actor");
            params.addValue("actor", criteria.actor().name());
        }
        if (criteria.q() != null && !criteria.q().isBlank()) {
            sql.append(" AND r.descripcion ILIKE :descriptionPattern ESCAPE '\\'");
            params.addValue("descriptionPattern", "%" + escapeLike(criteria.q().strip()) + "%");
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
