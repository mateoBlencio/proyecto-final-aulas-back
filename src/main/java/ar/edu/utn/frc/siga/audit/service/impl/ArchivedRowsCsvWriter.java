package ar.edu.utn.frc.siga.audit.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ArchivedRowsCsvWriter {

    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] REVISION_KINDS = {"CREATED", "MODIFIED", "DELETED"};

    private final Writer writer;
    private List<String> columns;

    ArchivedRowsCsvWriter(OutputStream out) throws IOException {
        this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write('﻿');
    }

    void write(Map<String, Object> row) throws IOException {
        if (columns == null) {
            columns = new ArrayList<>(row.keySet());
            writeRow(columns);
        }
        List<String> cells = new ArrayList<>(columns.size());
        for (String column : columns) {
            cells.add(cell(column, row.get(column)));
        }
        writeRow(cells);
    }

    void finish() throws IOException {
        writer.flush();
    }

    private static String cell(String column, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp timestamp) {
            return DATE_FORMAT.format(timestamp.toLocalDateTime());
        }
        if (value instanceof Number number && "revtype".equals(column)) {
            return REVISION_KINDS[number.intValue()];
        }
        if (value instanceof String text) {
            return AuditCsvWriter.text(text);
        }
        return value.toString();
    }

    private void writeRow(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(SEPARATOR);
            }
            writer.write(AuditCsvWriter.quoteIfNeeded(cells.get(i)));
        }
        writer.write(LINE_END);
    }
}
