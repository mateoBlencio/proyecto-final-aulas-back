package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
final class SubjectScope {

    private record Key(Integer specialty, Integer plan, Integer subject) {}

    private final Lazy<Set<Key>> allowed;

    SubjectScope(String subjectName, Lazy<List<RawSubject>> subjects) {
        this.allowed = Lazy.of(() -> {
            Set<Key> keys = subjects.get().stream()
                    .filter(raw -> raw.materiaNombre() != null && raw.materiaNombre().trim().equalsIgnoreCase(subjectName.trim()))
                    .map(raw -> new Key(raw.especialid(), raw.plan(), raw.materia()))
                    .collect(Collectors.toSet());
            if (keys.isEmpty()) {
                log.warn("Ninguna materia de SysAcad se llama '{}': el sync no traerá materias ni comisiones", subjectName);
            }
            return keys;
        });
    }

    List<RawSubject> subjects(List<RawSubject> rows) {
        return keep(rows, raw -> new Key(raw.especialid(), raw.plan(), raw.materia()));
    }

    List<RawCommission> commissions(List<RawCommission> rows) {
        return keep(rows, raw -> new Key(raw.especialid(), raw.plan(), raw.materia()));
    }

    List<RawSchedule> schedules(List<RawSchedule> rows) {
        return keep(rows, raw -> new Key(raw.especialidad(), raw.plan(), raw.materia()));
    }

    private <T> List<T> keep(List<T> rows, Function<T, Key> keyOf) {
        Set<Key> keys = allowed.get();
        return rows.stream().filter(row -> keys.contains(keyOf.apply(row))).toList();
    }
}
