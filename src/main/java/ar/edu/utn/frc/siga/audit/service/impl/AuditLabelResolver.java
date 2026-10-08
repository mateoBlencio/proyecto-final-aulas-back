package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the label of the records of a page: one call per provider, whatever the number of records. A failing
 * provider leaves its records without label; the log cannot fail because of a label. Each provider runs in its own
 * read-only transaction (REQUIRES_NEW): an exception crossing a transactional service proxy marks the current
 * transaction rollback-only, which would otherwise break the caller's commit.
 */
@Slf4j
@Component
public class AuditLabelResolver {

    private final Map<Class<?>, AuditLabelProvider> providers;

    private final TransactionTemplate isolated;

    public AuditLabelResolver(List<AuditLabelProvider> providers, PlatformTransactionManager transactionManager) {
        this.providers = providers.stream().collect(Collectors.toMap(AuditLabelProvider::entityType, Function.identity()));
        this.isolated = new TransactionTemplate(transactionManager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        isolated.setReadOnly(true);
    }

    /** Whether some provider labels records of {@code entity}. */
    public boolean supports(AuditedEntity entity) {
        return providers.containsKey(entity.javaType());
    }

    /**
     * Label of each record revision that has one. A record appearing in several revisions gets the label of its
     * latest revision in {@code states}.
     */
    public Map<RecordRevision, String> resolve(Map<RecordRevision, RecordStates> states) {
        Map<AuditedEntity, List<RecordRevision>> byEntity = states.keySet().stream()
                .collect(Collectors.groupingBy(RecordRevision::entity, LinkedHashMap::new, Collectors.toList()));
        Map<RecordRevision, String> result = new HashMap<>();
        byEntity.forEach((entity, keys) -> {
            AuditLabelProvider provider = providers.get(entity.javaType());
            if (provider == null) {
                return;
            }
            Map<String, RecordRevision> latestByRecord = keys.stream().collect(Collectors.toMap(
                    RecordRevision::recordId, Function.identity(),
                    (a, b) -> a.revision() >= b.revision() ? a : b));
            List<AuditedRecord> records = new ArrayList<>();
            latestByRecord.forEach((recordId, key) -> records.add(new AuditedRecord(recordId, valuesOf(states.get(key)))));
            records.sort(Comparator.comparing(AuditedRecord::recordId));
            try {
                Map<String, String> labels = isolated.execute(status -> provider.labels(records));
                for (RecordRevision key : keys) {
                    String label = labels.get(key.recordId());
                    if (label != null) {
                        result.put(key, label);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Label provider for {} failed; records left without label", entity.label(), e);
            }
        });
        return result;
    }

    // A DEL row carries the last state (store_data_at_delete); if it came empty, the previous row has it.
    private static Map<String, Object> valuesOf(RecordStates states) {
        boolean currentEmpty = states.current().values().stream().allMatch(Objects::isNull);
        return currentEmpty ? states.previous() : states.current();
    }
}
