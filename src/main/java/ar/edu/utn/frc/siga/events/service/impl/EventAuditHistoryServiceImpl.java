package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.EventAuditHistoryService;
import ar.edu.utn.frc.siga.common.audit.SigaRevision;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionKind;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * Implementación de la consulta del historial de auditoría de evento/occurrence sobre las
 * tablas {@code _aud} vía {@code AuditReader}. Cada query devuelve tuplas {entidad,
 * {@link SigaRevision}, {@link RevisionType}} en orden de revisión ascendente; en
 * revisiones DELETED el snapshot va en null por contrato.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAuditHistoryServiceImpl implements EventAuditHistoryService {

    private final EntityManager entityManager;
    private final AcademicEventRepository eventRepository;
    private final OccurrenceRepository occurrenceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<EventHistorySnapshotDto>> findEventHistory(Long eventId) {
        List<?> results = auditReader()
                .createQuery()
                .forRevisionsOfEntity(AcademicEvent.class, false, true)
                .add(AuditEntity.id().eq(eventId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return toRevisions(results, eventRepository, "AcademicEvent", eventId,
                entity -> toEventSnapshot((AcademicEvent) entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<OccurrenceHistorySnapshotDto>> findOccurrenceHistory(Long occurrenceId) {
        List<?> results = auditReader()
                .createQuery()
                .forRevisionsOfEntity(Occurrence.class, false, true)
                .add(AuditEntity.id().eq(occurrenceId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return toRevisions(results, occurrenceRepository, "Occurrence", occurrenceId, entity -> {
            Occurrence occurrence = (Occurrence) entity;
            return new OccurrenceHistorySnapshotDto(
                    occurrence.getId(),
                    occurrence.getEvent().getId(),
                    occurrence.getDate(),
                    occurrence.getStatus());
        });
    }

    private AuditReader auditReader() {
        return AuditReaderFactory.get(entityManager);
    }

    /**
     * Mapea las tuplas de Envers al contrato {@link RevisionDto}. Si el historial está vacío
     * y la entidad ancla tampoco existe hoy, el id nunca existió → 404 (historial vacío no
     * puede darse si la entidad existe: el INSERT siempre audita).
     */
    private <T> List<RevisionDto<T>> toRevisions(List<?> results, JpaRepository<?, Long> anchorRepository,
                                                 String anchorName, Long anchorId, Function<Object, T> snapshotMapper) {
        if (results.isEmpty() && !anchorRepository.existsById(anchorId)) {
            throw ResourceNotFoundException.of(anchorName, anchorId);
        }
        return results.stream()
                .map(row -> {
                    Object[] tuple = (Object[]) row;
                    SigaRevision revision = (SigaRevision) tuple[1];
                    RevisionType revisionType = (RevisionType) tuple[2];
                    return new RevisionDto<>(
                            revision.getId(),
                            revision.getFechaRevision(),
                            revision.getUsuario(),
                            toKind(revisionType),
                            revisionType == RevisionType.DEL ? null : snapshotMapper.apply(tuple[0]));
                })
                .toList();
    }

    private RevisionKind toKind(RevisionType revisionType) {
        return switch (revisionType) {
            case ADD -> RevisionKind.CREATED;
            case MOD -> RevisionKind.MODIFIED;
            case DEL -> RevisionKind.DELETED;
        };
    }

    /** Con herencia JOINED la query devuelve el subtipo real; se mapea a la variante del snapshot polimórfico. */
    private EventHistorySnapshotDto toEventSnapshot(AcademicEvent event) {
        return switch (event) {
            case RecurringEvent recurring -> new RecurringEventHistorySnapshotDto(
                    recurring.getId(), recurring.getType(), recurring.getEnrolled(), recurring.getStartTime(),
                    recurring.getDuration().toMinutes(), recurring.getDayOfWeek(), recurring.getStartDate(),
                    recurring.getEndDate(), recurring.getSubjectId(), recurring.getCommissionId());
            case UniqueEvent unique -> new UniqueEventHistorySnapshotDto(
                    unique.getId(), unique.getType(), unique.getEnrolled(), unique.getStartTime(),
                    unique.getDuration().toMinutes(), unique.getDate(), unique.getDescription(),
                    unique.getKind(), unique.getSubjectId(), unique.getCommissionId());
            default -> throw new IllegalStateException("Subtipo de evento no soportado: " + event.getClass());
        };
    }
}
