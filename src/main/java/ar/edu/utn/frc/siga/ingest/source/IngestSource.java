package ar.edu.utn.frc.siga.ingest.source;

import ar.edu.utn.frc.siga.ingest.dto.ImportedRow;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IngestSource {

    boolean supports(String filename);

    ParsedContent parse(MultipartFile file);

    record ParsedContent(List<ImportedRow> rows, int year) {
    }
}
