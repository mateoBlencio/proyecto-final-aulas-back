package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;

import java.time.LocalDate;

public interface AuditArchiveService {

    /**
     * Archives the audit of ocurrencia_aud and asignacion_aula_aud older than the cycle before the current
     * one (see {@code ArchiveCutoffResolver}). {@code today} is a parameter so tests can fix the day.
     */
    AuditArchiveResultDto archive(LocalDate today);
}
