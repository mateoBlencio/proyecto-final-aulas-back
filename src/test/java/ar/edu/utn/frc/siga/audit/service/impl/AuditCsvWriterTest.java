package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryType;
import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditCsvWriter")
class AuditCsvWriterTest {

    private static final LocalDateTime DATE = LocalDateTime.of(2026, 5, 4, 10, 30, 15);
    private static final String BOM = "﻿";

    private static AuditLogEntryDto change(String description, String recordId, String recordLabel) {
        return new AuditLogEntryDto(AuditLogEntryType.CHANGE, 7, DATE, "user@frc", ActorType.HUMAN, description,
                RevisionKind.MODIFIED, "Configuración", recordId, recordLabel, null, null, null, null, null);
    }

    private static AuditLogEntryDto operation(String description, String parentOperationId, Integer recordCount,
                                              List<String> entityTypes) {
        return new AuditLogEntryDto(AuditLogEntryType.OPERATION, 9, DATE, "user@frc", ActorType.SYSTEM, description,
                RevisionKind.CREATED, null, null, null, "op-1", parentOperationId, recordCount, entityTypes, null);
    }

    private static String render(AuditLogEntryDto... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AuditCsvWriter writer = new AuditCsvWriter(out)) {
            for (AuditLogEntryDto entry : entries) {
                writer.write(entry);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Lines after the header, splitting on CRLF (only valid when no cell contains a line break). */
    private static String[] rowCells(String csv) {
        String[] lines = csv.split("\r\n");
        return lines[1].split(";", -1);
    }

    @Test
    @DisplayName("empieza con el BOM UTF-8, la cabecera y CRLF")
    void startsWithBomAndHeader() throws IOException {
        String csv = render();

        assertThat(csv).isEqualTo(BOM + "fecha;tipo;actor;usuario;descripcion;entidades;cantidad;tipo_cambio;registro;"
                + "operacion;operacion_padre;revision\r\n");
        assertThat(csv.getBytes(StandardCharsets.UTF_8)).startsWith(0xEF, 0xBB, 0xBF);
    }

    @Test
    @DisplayName("una fila CHANGE respeta el orden de columnas y termina en CRLF")
    void changeRowColumnOrder() throws IOException {
        String csv = render(change("Edición", "42", "Aula 101"));

        assertThat(csv).endsWith("\r\n");
        assertThat(rowCells(csv)).containsExactly("2026-05-04 10:30:15", "CHANGE", "HUMAN", "user@frc", "Edición",
                "Configuración", "1", "MODIFIED", "Aula 101", "", "", "7");
    }

    @Test
    @DisplayName("valor con comillas, punto y coma y salto de línea va entre comillas con las comillas duplicadas")
    void quotesSemicolonAndNewline() throws IOException {
        String csv = render(change("dijo \"hola\"; luego\nchau", "1", null));

        assertThat(csv).contains(";\"dijo \"\"hola\"\"; luego\nchau\";");
    }

    @Test
    @DisplayName("un valor sin caracteres especiales no se entrecomilla")
    void plainValueIsNotQuoted() throws IOException {
        assertThat(render(change("simple", "1", null))).contains(";simple;").doesNotContain("\"");
    }

    @ParameterizedTest(name = "[{index}] descripción que empieza con {0}")
    @DisplayName("los textos que empiezan con carácter de fórmula llevan prefijo apóstrofo")
    @ValueSource(strings = {"=SUM(A1)", "+1", "-1", "@cmd", "\tx"})
    void formulaPrefixIsNeutralized(String description) throws IOException {
        String csv = render(change(description, "1", null));

        assertThat(rowCells(csv)[4]).isEqualTo("'" + description);
    }

    @Test
    @DisplayName("un texto que empieza con CR lleva apóstrofo y queda entre comillas")
    void carriageReturnPrefixIsNeutralized() throws IOException {
        String csv = render(change("\rx", "1", null));

        assertThat(csv).contains(";\"'\rx\";");
    }

    @Test
    @DisplayName("el apóstrofo solo se agrega al inicio del texto, no a un carácter de fórmula en el medio")
    void formulaCharInTheMiddleIsUntouched() throws IOException {
        assertThat(rowCells(render(change("a=b-c+d@e", "1", null)))[4]).isEqualTo("a=b-c+d@e");
    }

    @Test
    @DisplayName("el usuario, el registro y la operación también se neutralizan")
    void otherTextColumnsAreNeutralized() throws IOException {
        AuditLogEntryDto entry = new AuditLogEntryDto(AuditLogEntryType.OPERATION, 1, DATE, "=evil", ActorType.HUMAN,
                "d", RevisionKind.CREATED, null, null, null, "-op", "@parent", 2, List.of("A"), null);

        String[] cells = rowCells(render(entry));

        assertThat(cells[3]).isEqualTo("'=evil");
        assertThat(cells[9]).isEqualTo("'-op");
        assertThat(cells[10]).isEqualTo("'@parent");
    }

    @Test
    @DisplayName("cantidad y revisión negativas no llevan apóstrofo")
    void negativeNumbersAreNotPrefixed() throws IOException {
        AuditLogEntryDto entry = new AuditLogEntryDto(AuditLogEntryType.TRANSACTION, -5, DATE, "u", ActorType.HUMAN,
                "d", RevisionKind.MODIFIED, null, null, null, null, null, -3, List.of("A"), null);

        String[] cells = rowCells(render(entry));

        assertThat(cells[6]).isEqualTo("-3");
        assertThat(cells[11]).isEqualTo("-5");
    }

    @Test
    @DisplayName("un CHANGE usa recordLabel como registro")
    void changeUsesRecordLabel() throws IOException {
        assertThat(rowCells(render(change("d", "42", "Aula 101")))[8]).isEqualTo("Aula 101");
    }

    @Test
    @DisplayName("un CHANGE sin recordLabel usa recordId como registro")
    void changeFallsBackToRecordId() throws IOException {
        assertThat(rowCells(render(change("d", "42", null)))[8]).isEqualTo("42");
    }

    @Test
    @DisplayName("la cantidad es 1 en un CHANGE, que no trae recordCount")
    void changeCountIsOne() throws IOException {
        assertThat(rowCells(render(change("d", "42", null)))[6]).isEqualTo("1");
    }

    @Test
    @DisplayName("la cantidad de una OPERATION es su recordCount")
    void operationCountIsRecordCount() throws IOException {
        assertThat(rowCells(render(operation("d", null, 12, List.of("A"))))[6]).isEqualTo("12");
    }

    @Test
    @DisplayName("entidades de una OPERATION se unen con coma y espacio, y un CHANGE usa su entityType")
    void entitiesAreJoined() throws IOException {
        String operationCsv = render(operation("d", null, 3, List.of("Asignación", "Configuración")));
        String changeCsv = render(change("d", "1", null));

        // The joined cell contains no separator, so it stays unquoted.
        assertThat(rowCells(operationCsv)[5]).isEqualTo("Asignación, Configuración");
        assertThat(rowCells(changeCsv)[5]).isEqualTo("Configuración");
    }

    @Test
    @DisplayName("una fila con parentOperationId lo escribe en operacion_padre junto a operacion")
    void rowWithParentOperation() throws IOException {
        String[] cells = rowCells(render(operation("d", "op-parent", 2, List.of("A"))));

        assertThat(cells[9]).isEqualTo("op-1");
        assertThat(cells[10]).isEqualTo("op-parent");
    }

    @Test
    @DisplayName("campos nulos salen como celdas vacías sin la palabra null")
    void nullFieldsAreEmpty() throws IOException {
        AuditLogEntryDto entry = new AuditLogEntryDto(AuditLogEntryType.TRANSACTION, null, null, null, null, null,
                null, null, null, null, null, null, 2, null, null);

        String csv = render(entry);

        assertThat(csv).doesNotContain("null");
        assertThat(rowCells(csv)).containsExactly("", "TRANSACTION", "", "", "", "", "2", "", "", "", "", "");
    }

    @Test
    @DisplayName("varias filas quedan separadas por CRLF en el orden de escritura")
    void multipleRowsKeepOrder() throws IOException {
        String csv = render(change("primera", "1", null), change("segunda", "2", null));

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).contains("primera");
        assertThat(lines[2]).contains("segunda");
    }
}
