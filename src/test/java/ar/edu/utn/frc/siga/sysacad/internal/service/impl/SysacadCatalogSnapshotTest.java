package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.api.SysacadHolidayDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawHoliday;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysacadCatalogSnapshot")
class SysacadCatalogSnapshotTest {

    private final SysacadCatalogMapper mapper = new SysacadCatalogMapper();

    private SysacadCatalogSnapshot snapshot(List<RawSubject> subjects, List<RawSchedule> schedules) {
        return new SysacadCatalogSnapshot(
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(() -> schedules),
                Lazy.of(() -> subjects),
                Lazy.of(List::of),
                mapper);
    }

    private static RawSchedule scheduleRow(Integer especialidad, Integer plan, Integer materia,
            String materiaDictado, Integer horarioCuatrimestre) {
        return new RawSchedule(
                "1H90SR", 90, 805, 15, "Edif. Ing.Possetto",
                2, horarioCuatrimestre, "A", materiaDictado,
                "10:30", "12:45", "10:30-12:45", 135,
                especialidad, "Ingeniería en Sistemas de Información", plan, materia, "Sistemas de Representación", 30);
    }

    @Test
    @DisplayName("findSubjects: sin coincidencia en Schedule, term queda null")
    void findSubjectsSinCoincidencia() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(1, 1, 1, "A", 1))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
    }

    @Test
    @DisplayName("findSubjects: MateriaDictado 'A' resuelve term Anual sin mirar los cuatrimestres de comisión")
    void findSubjectsMateriaAnual() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, "A", 1), scheduleRow(17, 94, 519, "A", 2))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "Anual"));
    }

    @Test
    @DisplayName("findSubjects: MateriaDictado '1' resuelve term 1 Cuat.")
    void findSubjectsMateriaPrimerCuatrimestre() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, "1", 1))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "1 Cuat."));
    }

    @Test
    @DisplayName("findSubjects: MateriaDictado 'C' con comisiones en un solo cuatrimestre (ignorando el 0) toma ese cuatrimestre")
    void findSubjectsMateriaCuatrimestralUnSemestre() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, "C", 0), scheduleRow(17, 94, 519, "C", 2))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "2 Cuat."));
    }

    @Test
    @DisplayName("findSubjects: sin MateriaDictado y HorarioCuatrimestre 0 en todas las filas es Anual (igual que en eventos)")
    void findSubjectsSinDictadoConCeroEsAnual() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, null, 0), scheduleRow(17, 94, 519, null, 0))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "Anual"));
    }

    @Test
    @DisplayName("findSubjects: sin MateriaDictado, la mezcla {0,1} y {0,2} es Anual porque el 0 ya cubre ambos cuatrimestres")
    void findSubjectsSinDictadoConCeroMasCuatrimestreEsAnual() {
        List<SysacadSubjectDto> zeroAndFirst = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, null, 0), scheduleRow(17, 94, 519, null, 1))).findSubjects();
        List<SysacadSubjectDto> zeroAndSecond = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, null, 0), scheduleRow(17, 94, 519, null, 2))).findSubjects();

        assertThat(zeroAndFirst).extracting(SysacadSubjectDto::term).containsExactly("Anual");
        assertThat(zeroAndSecond).extracting(SysacadSubjectDto::term).containsExactly("Anual");
    }

    @Test
    @DisplayName("findSubjects: sin MateriaDictado y un solo cuatrimestre sin 0 toma ese cuatrimestre; los dos, null")
    void findSubjectsSinDictadoSinCero() {
        List<SysacadSubjectDto> single = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, null, 2))).findSubjects();
        List<SysacadSubjectDto> both = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, null, 1), scheduleRow(17, 94, 519, null, 2))).findSubjects();

        assertThat(single).extracting(SysacadSubjectDto::term).containsExactly("2 Cuat.");
        assertThat(both).extracting(SysacadSubjectDto::term).containsExactly((String) null);
    }

    @Test
    @DisplayName("findSubjects: MateriaDictado 'C' dictada en ambos cuatrimestres deja term null sin loguear conflicto")
    void findSubjectsMateriaCuatrimestralAmbosSemestres() {
        Logger logger = (Logger) LoggerFactory.getLogger(SysacadCatalogSnapshot.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        List<SysacadSubjectDto> subjects;
        try {
            subjects = snapshot(
                    List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                    List.of(scheduleRow(17, 94, 519, "C", 1), scheduleRow(17, 94, 519, "C", 2))).findSubjects();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("findSubjects: MateriaDictado inconsistente entre filas loguea WARN y deja term null")
    void findSubjectsMateriaDictadoInconsistente() {
        Logger logger = (Logger) LoggerFactory.getLogger(SysacadCatalogSnapshot.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        List<SysacadSubjectDto> subjects;
        try {
            subjects = snapshot(
                    List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                    List.of(scheduleRow(17, 94, 519, "A", 1), scheduleRow(17, 94, 519, "C", 2))).findSubjects();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("MateriaDictado en conflicto")
                && event.getFormattedMessage().contains("519"));
    }

    @Test
    @DisplayName("el catálogo de Schedule se lee una sola vez aunque lo consulten cuatro métodos distintos")
    void scheduleSeResuelveUnaSolaVezPorSnapshot() {
        AtomicInteger scheduleReads = new AtomicInteger();
        SysacadCatalogSnapshot snapshot = new SysacadCatalogSnapshot(
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(() -> {
                    scheduleReads.incrementAndGet();
                    return List.of(scheduleRow(17, 94, 519, "A", 1));
                }),
                Lazy.of(() -> List.of(new RawSubject(17, 94, 519, "Análisis Matemático I"))),
                Lazy.of(List::of),
                mapper);

        snapshot.findSubjects();
        snapshot.findSubjectCommissions();
        snapshot.findAcademicEvents();
        snapshot.findAllocations();

        assertThat(scheduleReads).hasValue(1);
    }

    @Test
    @DisplayName("findHolidays: skips rows with an invalid date and keeps the valid ones")
    void findHolidaysSkipsInvalidRows() {
        SysacadCatalogSnapshot snapshot = new SysacadCatalogSnapshot(
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(List::of),
                Lazy.of(() -> List.of(
                        new RawHoliday("20261208", "Inmaculada Concepción"),
                        new RawHoliday("20261332", "Invalid"))),
                mapper);

        assertThat(snapshot.findHolidays())
                .containsExactly(new SysacadHolidayDto(LocalDate.of(2026, 12, 8), "Inmaculada Concepción"));
    }
}
