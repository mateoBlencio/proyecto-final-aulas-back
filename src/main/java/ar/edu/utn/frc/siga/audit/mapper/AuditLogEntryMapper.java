package ar.edu.utn.frc.siga.audit.mapper;

import ar.edu.utn.frc.siga.audit.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
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
