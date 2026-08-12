package ar.edu.utn.frc.siga.ingest.service;

import ar.edu.utn.frc.siga.ingest.dto.IngestResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface IngestService {

    IngestResultDto ingestFile(MultipartFile file);
}
