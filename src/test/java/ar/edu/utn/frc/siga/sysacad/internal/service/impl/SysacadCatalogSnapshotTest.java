package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
                mapper);
    }

    private static RawSchedule scheduleRow(Integer especialidad, Integer plan, Integer materia, Integer horarioCuatrimestre) {
        return new RawSchedule(
                "1H90SR", 90, 805, 15, "Edif. Ing.Possetto",
                2, horarioCuatrimestre, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135,
                especialidad, "Ingeniería en Sistemas de Información", plan, materia, "Sistemas de Representación", 30);
    }

    @Test
    @DisplayName("findSubjects: sin coincidencia en Schedule, term queda null")
    void findSubjectsSinCoincidencia() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(1, 1, 1, 1))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
    }

    @Test
    @DisplayName("findSubjects: una sola coincidencia resuelve el term")
    void findSubjectsUnaCoincidencia() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, 1))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "1 Cuat."));
    }

    @Test
    @DisplayName("findSubjects: coincidencias consistentes (mismo semester en 2+ filas) resuelven el mismo term")
    void findSubjectsCoincidenciasConsistentes() {
        List<SysacadSubjectDto> subjects = snapshot(
                List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                List.of(scheduleRow(17, 94, 519, 2), scheduleRow(17, 94, 519, 2))).findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "2 Cuat."));
    }

    @Test
    @DisplayName("findSubjects: coincidencias en conflicto (semesters distintos) loguean WARN y dejan term null")
    void findSubjectsCoincidenciasEnConflicto() {
        Logger logger = (Logger) LoggerFactory.getLogger(SysacadCatalogSnapshot.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        List<SysacadSubjectDto> subjects;
        try {
            subjects = snapshot(
                    List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")),
                    List.of(scheduleRow(17, 94, 519, 1), scheduleRow(17, 94, 519, 2))).findSubjects();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("HorarioCuatrimestre en conflicto")
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
                    return List.of(scheduleRow(17, 94, 519, 1));
                }),
                Lazy.of(() -> List.of(new RawSubject(17, 94, 519, "Análisis Matemático I"))),
                mapper);

        snapshot.findSubjects();
        snapshot.findSubjectCommissions();
        snapshot.findAcademicEvents();
        snapshot.findAllocations();

        assertThat(scheduleReads).hasValue(1);
    }
}
