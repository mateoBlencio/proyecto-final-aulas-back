package ar.edu.utn.frc.siga.common.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Configuración central para todos los mappers MapStruct del proyecto.
 * Los mappers de cada módulo la referencian vía {@code @Mapper(config = CentralMapperConfig.class)}.
 * <p>
 * {@code common} es el único módulo OPEN (sin fronteras Modulith), por lo que puede
 * ser referenciado desde cualquier otro módulo sin declarar {@code allowedDependencies}.
 * <p>
 * Policy: {@link ReportingPolicy#ERROR} — cualquier propiedad del target no mapeada
 * explícitamente rompe la compilación. Esto obliga a decidir conscientemente (con
 * {@code @Mapping(ignore = true)} o similar) qué pasa con cada campo, en vez de dejar
 * huecos silenciosos. Si algún mapper puntual necesita relajarla por falsos positivos
 * irreducibles, debe documentarlo con {@code @Mapper(unmappedTargetPolicy = ...)} propio
 * y una justificación en el javadoc de esa interfaz.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CentralMapperConfig {
}
