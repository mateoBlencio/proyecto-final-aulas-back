package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.repository.AuditChangeRow;
import ar.edu.utn.frc.siga.audit.repository.AuditLogCriteria;
import ar.edu.utn.frc.siga.audit.repository.AuditLogQueryRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.repository.ChangeScope;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.audit.service.impl.AuditLabelResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the description of an operation that names the records it changed: "{base}: A, B y 15 más". The names are
 * the summary labels of the records of the operation's subject entity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OperationSummary {

    private static final int PAGE_SIZE = 5000;
    private static final int MAX_NAMES_LISTED = 3;
    private static final int NAMES_LISTED_WHEN_MORE = 2;

    private final AuditedEntityRegistry registry;
    private final AuditLogQueryRepository queryRepository;
    private final AuditRecordStateRepository stateRepository;
    private final AuditLabelResolver labelResolver;

    String describe(String operationId, String base, String subject) {
        try {
            AuditedEntity entity = registry.bySimpleName(subject).orElse(null);
            if (entity == null) {
                log.warn("Entidad auditada desconocida para el resumen de la operación {}: {}", operationId, subject);
                return base;
            }
            return compose(base, new ArrayList<>(namesOf(operationId, entity)));
        } catch (RuntimeException e) {
            log.warn("No se pudo armar el resumen de la operación {}", operationId, e);
            return base;
        }
    }

    private Set<String> namesOf(String operationId, AuditedEntity entity) {
        AuditLogCriteria criteria = new AuditLogCriteria(null, null, null, null, List.of(entity), null, null);
        ChangeScope scope = ChangeScope.ofOperation(operationId);
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        Set<String> names = new TreeSet<>(collator);
        for (int page = 0; ; page++) {
            List<AuditChangeRow> rows = queryRepository.findChanges(criteria, scope, PageRequest.of(page, PAGE_SIZE));
            Map<RecordRevision, RecordStates> states = stateRepository.load(rows.stream()
                    .map(row -> new RecordRevision(row.entity(), row.metadata().recordId(), row.metadata().revision()))
                    .toList());
            names.addAll(labelResolver.resolveSummaries(states).values());
            if (rows.size() < PAGE_SIZE) {
                return names;
            }
        }
    }

    static String compose(String base, List<String> sortedNames) {
        if (sortedNames.isEmpty()) {
            return base;
        }
        int total = sortedNames.size();
        for (int listed = total <= MAX_NAMES_LISTED ? total : NAMES_LISTED_WHEN_MORE; listed >= 1; listed--) {
            String text = base + ": " + list(sortedNames.subList(0, listed), total - listed);
            if (text.length() <= AuditOperationContext.MAX_DESCRIPTION_LENGTH) {
                return text;
            }
        }
        return AuditOperationContext.truncate(base + ": " + list(sortedNames.subList(0, 1), total - 1));
    }

    private static String list(List<String> shown, int remaining) {
        if (remaining > 0) {
            return String.join(", ", shown) + " y " + remaining + " más";
        }
        if (shown.size() == 1) {
            return shown.getFirst();
        }
        return String.join(", ", shown.subList(0, shown.size() - 1)) + " y " + shown.getLast();
    }
}
