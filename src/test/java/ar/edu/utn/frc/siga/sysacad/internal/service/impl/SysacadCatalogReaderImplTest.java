package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadCatalogReaderImpl")
class SysacadCatalogReaderImplTest {

    @Mock
    private ScheduleViewFetcher scheduleViewFetcher;

    @Mock
    private SubjectViewFetcher subjectViewFetcher;

    private final SysacadCatalogMapper mapper = new SysacadCatalogMapper();

    private SysacadCatalogReaderImpl reader() {
        return new SysacadCatalogReaderImpl(
                null, null, null, null,
                scheduleViewFetcher, subjectViewFetcher, Optional.empty(), mapper);
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
        when(subjectViewFetcher.fetch()).thenReturn(List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")));
        when(scheduleViewFetcher.fetch()).thenReturn(List.of(scheduleRow(1, 1, 1, 1)));

        List<SysacadSubjectDto> subjects = reader().findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
    }

    @Test
    @DisplayName("findSubjects: una sola coincidencia resuelve el term")
    void findSubjectsUnaCoincidencia() {
        when(subjectViewFetcher.fetch()).thenReturn(List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")));
        when(scheduleViewFetcher.fetch()).thenReturn(List.of(scheduleRow(17, 94, 519, 1)));

        List<SysacadSubjectDto> subjects = reader().findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "1 Cuat."));
    }

    @Test
    @DisplayName("findSubjects: coincidencias consistentes (mismo semester en 2+ filas) resuelven el mismo term")
    void findSubjectsCoincidenciasConsistentes() {
        when(subjectViewFetcher.fetch()).thenReturn(List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")));
        when(scheduleViewFetcher.fetch()).thenReturn(
                List.of(scheduleRow(17, 94, 519, 2), scheduleRow(17, 94, 519, 2)));

        List<SysacadSubjectDto> subjects = reader().findSubjects();

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", "2 Cuat."));
    }

    @Test
    @DisplayName("findSubjects: coincidencias en conflicto (semesters distintos) loguean WARN y dejan term null")
    void findSubjectsCoincidenciasEnConflicto() {
        when(subjectViewFetcher.fetch()).thenReturn(List.of(new RawSubject(17, 94, 519, "Análisis Matemático I")));
        when(scheduleViewFetcher.fetch()).thenReturn(
                List.of(scheduleRow(17, 94, 519, 1), scheduleRow(17, 94, 519, 2)));

        Logger logger = (Logger) LoggerFactory.getLogger(SysacadCatalogReaderImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        List<SysacadSubjectDto> subjects;
        try {
            subjects = reader().findSubjects();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(subjects).containsExactly(new SysacadSubjectDto(17, 94, 519, "Análisis Matemático I", null));
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("HorarioCuatrimestre en conflicto")
                && event.getFormattedMessage().contains("519"));
    }
}
