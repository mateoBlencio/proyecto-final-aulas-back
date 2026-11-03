package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.audit.service.impl.FieldValueFormatter.ReferenceKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Labels of the records referenced by the audited values of a page, at their latest revision: one state load and
 * one label call per referenced entity type.
 */
@Component
@RequiredArgsConstructor
public class ReferenceLabelLoader {

    private final AuditedEntityRegistry registry;
    private final AuditRecordStateRepository stateRepository;
    private final AuditLabelResolver labelResolver;

    public Map<ReferenceKey, String> load(Map<RecordRevision, RecordStates> states) {
        Map<AuditedEntity, Set<String>> idsByTarget = new LinkedHashMap<>();
        states.forEach((key, recordStates) -> {
            for (AuditedColumn column : key.entity().columns()) {
                if (column.targetType() == null) {
                    continue;
                }
                registry.byType(column.targetType())
                        .filter(labelResolver::supports)
                        .ifPresent(target -> {
                            Set<String> ids = idsByTarget.computeIfAbsent(target, t -> new HashSet<>());
                            addId(ids, recordStates.current().get(column.property()));
                            addId(ids, recordStates.previous().get(column.property()));
                        });
            }
        });

        Map<ReferenceKey, String> labels = new HashMap<>();
        idsByTarget.forEach((target, ids) -> {
            Map<RecordRevision, RecordStates> latest = stateRepository.loadLatest(target, ids);
            labelResolver.resolve(latest).forEach((key, label) ->
                    labels.put(new ReferenceKey(target.javaType(), key.recordId()), label));
        });
        return labels;
    }

    private static void addId(Set<String> ids, Object value) {
        if (value != null) {
            ids.add(value.toString());
        }
    }
}
