package ar.edu.utn.frc.siga.audit.service;

import ar.edu.utn.frc.siga.audit.dto.response.AuditArchivedCycleDto;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;

public interface AuditArchiveExportService {

    List<AuditArchivedCycleDto> findArchivedCycles();

    void exportCycle(int cycle, Supplier<OutputStream> output);
}
