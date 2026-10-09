package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditOperationChainDto;
import ar.edu.utn.frc.siga.audit.mapper.AuditLogEntryMapper;
import ar.edu.utn.frc.siga.audit.repository.AuditChainRows;
import ar.edu.utn.frc.siga.audit.repository.AuditChangeRow;
import ar.edu.utn.frc.siga.audit.repository.AuditGroupRow;
import ar.edu.utn.frc.siga.audit.repository.AuditLogCriteria;
import ar.edu.utn.frc.siga.audit.repository.AuditLogQueryRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.repository.ChangeScope;
import ar.edu.utn.frc.siga.audit.service.AuditRegistryService;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRegistryServiceImpl implements AuditRegistryService {

    private static final int EXPORT_PAGE_SIZE = 500;

    private final AuditLogQueryRepository repository;
    private final AuditRecordStateRepository stateRepository;
    private final AuditedEntityRegistry registry;
    private final AuditLogEntryMapper auditLogEntryMapper;
    private final AuditLabelResolver labelResolver;

    @Value("${siga.audit.export.max-rows:10000}")
    private long exportMaxRows;

    @PostConstruct
    void validateExportMaxRows() {
        if (exportMaxRows <= 0) {
            throw new IllegalStateException("siga.audit.export.max-rows debe ser mayor que 0 (valor actual: "
                    + exportMaxRows + ")");
        }
    }

    // No method of this service is transactional: queries are read-only JDBC with no JPA/Envers access, and each
    // label provider runs in its own REQUIRES_NEW transaction, so an outer one would only hold a second connection
    // per request. Under READ COMMITTED each statement already had its own snapshot inside the transaction.
    @Override
    public Page<AuditLogEntryDto> findAll(AuditLogFilter filter, Pageable pageable) {
        AuditLogCriteria criteria = toCriteria(filter);

        long total = repository.countGroups(criteria);
        return new PageImpl<>(loadGroupPage(criteria, pageable), pageable, total);
    }

    /**
     * Streams the listing as CSV. The filter is validated, the rows counted and the first page loaded before
     * {@code output} is asked for the stream, so any failure up to the first byte (invalid filter, over the limit,
     * query error) is still answered as a normal error. No transaction wraps the export: each page is read
     * without holding a connection while the client downloads. {@code countGroups} already bounds the total, so
     * the loop ends on the first short page. If a later page fails the response is already committed: the error is
     * logged and rethrown, and the client receives a truncated file. Revisions written during the export can
     * shift the page boundaries and repeat a row.
     */
    @Override
    public void exportCsv(AuditLogFilter filter, Supplier<OutputStream> output) {
        AuditLogCriteria criteria = toCriteria(filter);
        long total = repository.countGroups(criteria);
        if (total > exportMaxRows) {
            throw new InvalidSelectionException("El filtro devuelve " + total + " entradas y el máximo para exportar es "
                    + exportMaxRows + ". Acotá el rango de fechas o agregá filtros.");
        }
        List<AuditLogEntryDto> entries = loadGroupPage(criteria, PageRequest.of(0, EXPORT_PAGE_SIZE));
        try (AuditCsvWriter csv = new AuditCsvWriter(output.get())) {
            for (int page = 0; ; ) {
                for (AuditLogEntryDto entry : entries) {
                    csv.write(entry);
                }
                csv.flush();
                if (entries.size() < EXPORT_PAGE_SIZE) {
                    break;
                }
                page++;
                try {
                    entries = loadGroupPage(criteria, PageRequest.of(page, EXPORT_PAGE_SIZE));
                } catch (RuntimeException e) {
                    log.error("Audit CSV export failed on page {} (filter={}); the response is truncated",
                            page, filter, e);
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // One page of listing entries; single-change labels are resolved in one call per entity type of the page.
    private List<AuditLogEntryDto> loadGroupPage(AuditLogCriteria criteria, Pageable pageable) {
        List<AuditGroupRow> groups = repository.findGroups(criteria, pageable);
        Map<RecordRevision, String> labels = loadSingleChangeLabels(groups);
        return groups.stream()
                .map(row -> toEntry(row, labels))
                .toList();
    }

    @Override
    public Page<AuditLogEntryDto> findOperationItems(String operationId, AuditLogFilter filter, Pageable pageable) {
        return findItems(toCriteria(filter), ChangeScope.ofOperation(operationId), pageable);
    }

    @Override
    public Page<AuditLogEntryDto> findRevisionItems(int revision, AuditLogFilter filter, Pageable pageable) {
        return findItems(toCriteria(filter), ChangeScope.ofRevision(revision), pageable);
    }

    @Override
    public Page<AuditLogEntryDto> findEntityHistory(String entityType, String recordId, Pageable pageable) {
        AuditedEntity entity = registry.byLabel(entityType)
                .orElseThrow(() -> new InvalidSelectionException("Tipo de entidad desconocido: '" + entityType + "'"));
        AuditLogCriteria criteria = new AuditLogCriteria(null, null, null, null, List.of(entity), null, null);
        return findItems(criteria, ChangeScope.ofRecord(entity, recordId), pageable);
    }

    @Override
    public AuditOperationChainDto findOperationChain(String operationId) {
        AuditChainRows chain = repository.findOperationChain(operationId);
        List<AuditLogEntryDto> entries = chain.rows().stream()
                .sorted(Comparator.comparingInt(AuditGroupRow::revision))
                .map(auditLogEntryMapper::toOperation)
                .toList();
        return new AuditOperationChainDto(entries, chain.truncated());
    }

    @Override
    public List<String> findEntityTypes() {
        return registry.all().stream().map(AuditedEntity::label).toList();
    }

    private Page<AuditLogEntryDto> findItems(AuditLogCriteria criteria, ChangeScope scope, Pageable pageable) {
        long total = repository.countChanges(criteria, scope);
        List<AuditChangeRow> rows = repository.findChanges(criteria, scope, pageable);
        // Diff only for the requested page: one query per entity type present in it.
        Map<RecordRevision, RecordStates> states = stateRepository.load(rows.stream()
                .map(row -> new RecordRevision(row.entity(), row.metadata().recordId(), row.metadata().revision()))
                .toList());
        Map<RecordRevision, String> labels = labelResolver.resolve(states);
        List<AuditLogEntryDto> content = rows.stream()
                .map(row -> {
                    RecordRevision key = new RecordRevision(row.entity(), row.metadata().recordId(), row.metadata().revision());
                    RecordStates recordStates = states.get(key);
                    if (recordStates == null) {
                        log.warn("Audited state not found for {} id={} rev={}; changes left null",
                                row.entity().label(), key.recordId(), key.revision());
                    }
                    return auditLogEntryMapper.toChange(row.metadata(), row.entity().label(), labels.get(key),
                            recordStates == null ? null
                                    : FieldDiffCalculator.calculate(row.entity(), row.metadata().kind(), recordStates));
                })
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    // Standalone single-record changes of the listing: one state load per entity type of the page.
    private Map<RecordRevision, String> loadSingleChangeLabels(List<AuditGroupRow> groups) {
        List<RecordRevision> keys = groups.stream()
                .filter(AuditRegistryServiceImpl::isSingleChange)
                .map(this::singleChangeKey)
                .flatMap(Optional::stream)
                .filter(key -> labelResolver.supports(key.entity()))
                .toList();
        return labelResolver.resolve(stateRepository.load(keys));
    }

    private Optional<RecordRevision> singleChangeKey(AuditGroupRow row) {
        return registry.byLabel(row.singleEntityType())
                .map(entity -> new RecordRevision(entity, row.singleRecordId(), row.revision()));
    }

    private static boolean isSingleChange(AuditGroupRow row) {
        return row.operationId() == null && row.recordCount() == 1;
    }

    private AuditLogEntryDto toEntry(AuditGroupRow row, Map<RecordRevision, String> labels) {
        if (row.operationId() != null) {
            return auditLogEntryMapper.toOperation(row);
        }
        if (row.recordCount() == 1) {
            RevisionMetadata metadata = new RevisionMetadata(row.singleRecordId(), row.revision(), row.date(),
                    row.user(), row.actorType(), row.commonKind(), row.description(), null);
            String label = singleChangeKey(row).map(labels::get).orElse(null);
            return auditLogEntryMapper.toChange(metadata, row.singleEntityType(), label, null);
        }
        return auditLogEntryMapper.toTransaction(row);
    }

    private AuditLogCriteria toCriteria(AuditLogFilter filter) {
        DateRanges.requireNotBefore(filter.to(), filter.from());

        List<AuditedEntity> targets = resolveTargets(filter.entityType());
        LocalDateTime from = atStartOfDay(filter.from());
        LocalDateTime toExclusive = filter.to() != null ? filter.to().plusDays(1).atStartOfDay() : null;
        return new AuditLogCriteria(from, toExclusive, filter.user(), filter.kind(), targets, filter.actor(), filter.q());
    }

    private List<AuditedEntity> resolveTargets(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return registry.all();
        }
        return List.of(registry.byLabel(entityType)
                .orElseThrow(() -> new InvalidSelectionException(
                        "Tipo de entidad desconocido: '" + entityType + "'")));
    }

    private static LocalDateTime atStartOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }
}
