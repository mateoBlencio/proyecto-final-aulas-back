package ar.edu.utn.frc.siga.events.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SuspendedEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, LocalDate> findAllSuspendedFrom() {
        Map<Long, LocalDate> result = new HashMap<>();
        jdbc.query("SELECT id_evento_academico, fecha_baja FROM evento_academico_suspendido",
                rs -> {
                    result.put(rs.getLong("id_evento_academico"), rs.getObject("fecha_baja", LocalDate.class));
                });
        return result;
    }

    public Set<Long> findIdsAmong(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT id_evento_academico FROM evento_academico_suspendido WHERE id_evento_academico IN (:ids)",
                new MapSqlParameterSource("ids", eventIds), Long.class));
    }
}
