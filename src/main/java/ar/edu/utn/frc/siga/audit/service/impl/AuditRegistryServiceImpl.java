package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.RevisionReader;
import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryType;
import ar.edu.utn.frc.siga.audit.mapper.AuditLogEntryMapper;
import ar.edu.utn.frc.siga.audit.registry.AuditedEntity;
import ar.edu.utn.frc.siga.audit.registry.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.audit.service.AuditRegistryService;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRegistryServiceImpl implements AuditRegistryService {

    private static final Comparator<AuditLogEntryDto> BY_REVISION_DESC =
            Comparator.comparing(AuditLogEntryDto::revision).reversed()
                    .thenComparing(entry -> entry.entityType() == null ? "" : entry.entityType())
                    .thenComparing(entry -> entry.recordId() == null ? "" : entry.recordId());

    private final RevisionReader revisionReader;
    private final AuditedEntityRegistry registry;
    private final AuditLogEntryMapper auditLogEntryMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogEntryDto> findAll(AuditLogFilter filter, Pageable pageable) {
        DateRanges.requireNotBefore(filter.to(), filter.from());

        Collection<AuditedEntity> targets = resolveTargets(filter.entityType());
        LocalDateTime from = atStartOfDay(filter.from());
        LocalDateTime toExclusive = filter.to() != null ? filter.to().plusDays(1).atStartOfDay() : null;

        List<Row> rows = targets.stream()
                .flatMap(target -> revisionReader
                        .readMetadata(target.javaType(), from, toExclusive, filter.user(), filter.kind(), null)
                        .stream()
                        .map(metadata -> new Row(metadata, target.label())))
                .toList();

        Stream<AuditLogEntryDto> changes = rows.stream()
                .filter(row -> row.metadata().operationId() == null)
                .map(row -> auditLogEntryMapper.toChange(row.metadata(), row.label()));

        Stream<AuditLogEntryDto> operations = rows.stream()
                .filter(row -> row.metadata().operationId() != null)
                .collect(Collectors.groupingBy(row -> row.metadata().operationId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(this::toOperationEntry);

        List<AuditLogEntryDto> entries = Stream.concat(changes, operations)
                .sorted(BY_REVISION_DESC)
                .toList();

        return Paging.of(entries, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogEntryDto> findOperationItems(String operationId, Pageable pageable) {
        List<AuditLogEntryDto> items = registry.all().stream()
                .flatMap(target -> revisionReader
                        .readMetadata(target.javaType(), null, null, null, null, operationId)
                        .stream()
                        .map(metadata -> auditLogEntryMapper.toChange(metadata, target.label())))
                .sorted(BY_REVISION_DESC)
                .toList();

        return Paging.of(items, pageable);
    }

    private AuditLogEntryDto toOperationEntry(List<Row> group) {
        RevisionMetadata sample = group.getFirst().metadata();
        int latestRevision = group.stream().mapToInt(row -> row.metadata().revision()).max().orElse(0);
        LocalDateTime latestDate = group.stream()
                .map(row -> row.metadata().date())
                .max(Comparator.naturalOrder())
                .orElse(sample.date());
        List<String> entityTypes = group.stream()
                .map(Row::label)
                .distinct()
                .sorted()
                .toList();

        return new AuditLogEntryDto(
                AuditLogEntryType.OPERATION,
                latestRevision,
                latestDate,
                sample.user(),
                sample.description(),
                null,
                null,
                null,
                sample.operationId(),
                group.size(),
                entityTypes);
    }

    private Collection<AuditedEntity> resolveTargets(String entityType) {
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

    private record Row(RevisionMetadata metadata, String label) {
    }
}
