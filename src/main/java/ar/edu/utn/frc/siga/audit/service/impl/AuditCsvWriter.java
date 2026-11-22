package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes audit entries as CSV (RFC 4180 quoting, {@code ;} separator, CRLF line ends) to a stream. The BOM goes
 * first so Excel detects UTF-8. Every text cell that starts with a formula character gets a {@code '} prefix.
 */
public class AuditCsvWriter implements AutoCloseable {

    static final String HEADER = "fecha;tipo;actor;usuario;descripcion;entidades;cantidad;tipo_cambio;registro;"
            + "operacion;operacion_padre;revision";

    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Writer writer;

    public AuditCsvWriter(OutputStream out) throws IOException {
        this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write('\uFEFF');
        writer.write(HEADER);
        writer.write(LINE_END);
    }

    public void write(AuditLogEntryDto entry) throws IOException {
        // A single change has no recordCount; it is one record.
        Integer count = entry.recordCount() != null ? entry.recordCount() : Integer.valueOf(1);
        String entities = entry.entityTypes() != null ? String.join(", ", entry.entityTypes()) : entry.entityType();
        String record = entry.recordLabel() != null ? entry.recordLabel() : entry.recordId();
        writeRow(List.of(
                entry.date() == null ? "" : DATE_FORMAT.format(entry.date()),
                entry.type().name(),
                entry.actorType() == null ? "" : entry.actorType().name(),
                text(entry.user()),
                text(entry.description()),
                text(entities),
                String.valueOf(count),
                entry.kind() == null ? "" : entry.kind().name(),
                text(record),
                text(entry.operationId()),
                text(entry.parentOperationId()),
                entry.revision() == null ? "" : String.valueOf(entry.revision())));
    }

    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private void writeRow(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(SEPARATOR);
            }
            writer.write(quoteIfNeeded(cells.get(i)));
        }
        writer.write(LINE_END);
    }

    // Free text: neutralizes formulas. Dates, enums and numbers do not go through here.
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> "'" + value;
            default -> value;
        };
    }

    static String quoteIfNeeded(String cell) {
        if (cell.indexOf(SEPARATOR) < 0 && cell.indexOf('"') < 0 && cell.indexOf('\n') < 0 && cell.indexOf('\r') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
