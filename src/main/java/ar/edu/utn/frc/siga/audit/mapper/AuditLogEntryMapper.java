package ar.edu.utn.frc.siga.audit.mapper;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryType;
import ar.edu.utn.frc.siga.audit.repository.AuditGroupRow;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface AuditLogEntryMapper {

    @Mapping(target = "type", constant = "CHANGE")
    @Mapping(target = "entityType", source = "entityType")
    @Mapping(target = "description", expression = "java(describe(metadata, entityType))")
    @Mapping(target = "recordCount", ignore = true)
    @Mapping(target = "entityTypes", ignore = true)
    AuditLogEntryDto toChange(RevisionMetadata metadata, String entityType);

    default AuditLogEntryDto toOperation(AuditGroupRow row) {
        return new AuditLogEntryDto(AuditLogEntryType.OPERATION, row.revision(), row.date(), row.user(),
                row.description(), row.commonKind(), null, null, row.operationId(),
                (int) row.recordCount(), row.entityTypes());
    }

    default AuditLogEntryDto toTransaction(AuditGroupRow row) {
        return new AuditLogEntryDto(AuditLogEntryType.TRANSACTION, row.revision(), row.date(), row.user(),
                describeTransaction(row), row.commonKind(), null, null, null,
                (int) row.recordCount(), row.entityTypes());
    }

    default String describeTransaction(AuditGroupRow row) {
        return row.recordCount() + " cambios en " + String.join(", ", row.entityTypes());
    }

    /** Descripción escrita por el código de la operación, o una derivada del tipo de cambio. */
    default String describe(RevisionMetadata metadata, String entityType) {
        if (metadata.description() != null && !metadata.description().isBlank()) {
            return metadata.description();
        }
        return switch (metadata.kind()) {
            case CREATED -> "Alta de " + entityType;
            case MODIFIED -> "Modificación de " + entityType;
            case DELETED -> "Baja de " + entityType;
        };
    }
}
