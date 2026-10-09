package ar.edu.utn.frc.siga.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Key of a revision summary row: root {@code _aud} table and Envers revision type (0 add, 1 mod, 2 del). */
@Embeddable
public record RevisionSummaryKey(
        @Column(name = "tabla_aud", nullable = false, length = 63) String auditTable,
        @Column(name = "revtype", nullable = false) short revtype) {
}
