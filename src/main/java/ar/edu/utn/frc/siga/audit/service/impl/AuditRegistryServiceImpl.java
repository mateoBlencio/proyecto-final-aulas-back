package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.mapper.AuditLogEntryMapper;
import ar.edu.utn.frc.siga.audit.repository.AuditChangeRow;
import ar.edu.utn.frc.siga.audit.repository.AuditGroupRow;
import ar.edu.utn.frc.siga.audit.repository.AuditLogCriteria;
import ar.edu.utn.frc.siga.audit.repository.AuditLogQueryRepository;
import ar.edu.utn.frc.siga.audit.repository.ChangeScope;
import ar.edu.utn.frc.siga.audit.service.AuditRegistryService;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRegistryServiceImpl implements AuditRegistryService {

    private final AuditLogQueryRepository repository;
    private final AuditedEntityRegistry registry;
    private final AuditLogEntryMapper auditLogEntryMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogEntryDto> findAll(AuditLogFilter filter, Pageable pageable) {
        AuditLogCriteria criteria = toCriteria(filter);

        long total = repository.countGroups(criteria);
        List<AuditLogEntryDto> content = repository.findGroups(criteria, pageable).stream()
                .map(this::toEntry)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogEntryDto> findOperationItems(String operationId, AuditLogFilter filter, Pageable pageable) {
        return findItems(toCriteria(filter), ChangeScope.ofOperation(operationId), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogEntryDto> findRevisionItems(int revision, AuditLogFilter filter, Pageable pageable) {
        return findItems(toCriteria(filter), ChangeScope.ofRevision(revision), pageable);
    }

    @Override
    public List<String> findEntityTypes() {
        return registry.all().stream().map(AuditedEntity::label).toList();
    }

    private Page<AuditLogEntryDto> findItems(AuditLogCriteria criteria, ChangeScope scope, Pageable pageable) {
        long total = repository.countChanges(criteria, scope);
        List<AuditLogEntryDto> content = repository.findChanges(criteria, scope, pageable).stream()
                .map((AuditChangeRow row) -> auditLogEntryMapper.toChange(row.metadata(), row.entityType()))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    private AuditLogEntryDto toEntry(AuditGroupRow row) {
        if (row.operationId() != null) {
            return auditLogEntryMapper.toOperation(row);
        }
        if (row.recordCount() == 1) {
            RevisionMetadata metadata = new RevisionMetadata(row.singleRecordId(), row.revision(), row.date(),
                    row.user(), row.commonKind(), row.description(), null);
            return auditLogEntryMapper.toChange(metadata, row.singleEntityType());
        }
        return auditLogEntryMapper.toTransaction(row);
    }

    private AuditLogCriteria toCriteria(AuditLogFilter filter) {
        DateRanges.requireNotBefore(filter.to(), filter.from());

        List<AuditedEntity> targets = resolveTargets(filter.entityType());
        LocalDateTime from = atStartOfDay(filter.from());
        LocalDateTime toExclusive = filter.to() != null ? filter.to().plusDays(1).atStartOfDay() : null;
        return new AuditLogCriteria(from, toExclusive, filter.user(), filter.kind(), targets);
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
