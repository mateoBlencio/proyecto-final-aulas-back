package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.hibernate.Hibernate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapea la jerarquía {@link AcademicEvent} (recurrente/único) a su DTO sellado
 * correspondiente. MapStruct no puede generar el despacho polimórfico por sí solo
 * (la entidad puede llegar proxied por Hibernate), así que el método de entrada es
 * un {@code default} que desproxya y delega en el método concreto según el subtipo;
 * cada método concreto sí es MapStruct puro.
 */
@Mapper(config = CentralMapperConfig.class)
public interface AcademicEventMapper {

    default AcademicEventResponseDto toDto(AcademicEvent event, SubjectResponseDto subject, CommissionResponseDto commission) {
        AcademicEvent realEvent = (AcademicEvent) Hibernate.unproxy(event);
        if (realEvent instanceof RecurringEvent r) {
            return toDto(r, subject, commission);
        }
        return toDto((UniqueEvent) realEvent, subject, commission);
    }

    // "subject"/"commission" se fuerzan a mapear el parámetro entero: el evento solo
    // tiene subjectId/commissionId (Long) — el SubjectResponseDto/CommissionResponseDto
    // siempre viene resuelto por el composer, nunca navegando la entidad.
    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "type", constant = "RECURRING")
    @Mapping(target = "durationMinutes", expression = "java(event.getDuration().toMinutes())")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "commission", source = "commission")
    RecurringEventResponseDto toDto(RecurringEvent event, SubjectResponseDto subject, CommissionResponseDto commission);

    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "type", constant = "UNIQUE_EVENT")
    @Mapping(target = "durationMinutes", expression = "java(event.getDuration().toMinutes())")
    UniqueEventResponseDto toDto(UniqueEvent event, SubjectResponseDto subject, CommissionResponseDto commission);
}
