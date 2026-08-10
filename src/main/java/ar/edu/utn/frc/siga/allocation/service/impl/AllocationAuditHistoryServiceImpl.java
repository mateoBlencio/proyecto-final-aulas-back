package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.service.AllocationAuditHistoryService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación de la consulta del historial de auditoría de asignaciones sobre
 * {@code asignacion_aula_aud} vía {@code AuditReader}. Devuelve tuplas {entidad,
 * {@link SigaRevision}, {@link RevisionType}} en orden de revisión ascendente; en
 * revisiones DELETED el snapshot va en null por contrato.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationAuditHistoryServiceImpl implements AllocationAuditHistoryService {

    private final EntityManager entityManager;
    private final OccurrenceService occurrenceService;

    @Override
    @Transactional(readOnly = true)
    public List<RevisionDto<AllocationHistorySnapshotDto>> findAllocationHistory(Long occurrenceId) {
        List<?> results = auditReader()
                .createQuery()
                .forRevisionsOfEntity(Allocation.class, false, true)
                .add(AuditEntity.property("occurrenceId").eq(occurrenceId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        if (results.isEmpty() && !occurrenceService.existsOccurrence(occurrenceId)) {
            throw ResourceNotFoundException.of("Occurrence", occurrenceId);
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
                            revisionType == RevisionType.DEL ? null : toSnapshot((Allocation) tuple[0], occurrenceId));
                })
                .toList();
    }

    private AuditReader auditReader() {
        return AuditReaderFactory.get(entityManager);
    }

    private AllocationHistorySnapshotDto toSnapshot(Allocation allocation, Long occurrenceId) {
        return new AllocationHistorySnapshotDto(
                allocation.getId(),
                occurrenceId,
                allocation.getClassroomId(),
                allocation.getSource(),
                allocation.getCreatedAt(),
                allocation.getObservation());
    }

    private RevisionKind toKind(RevisionType revisionType) {
        return switch (revisionType) {
            case ADD -> RevisionKind.CREATED;
            case MOD -> RevisionKind.MODIFIED;
            case DEL -> RevisionKind.DELETED;
        };
    }
}
