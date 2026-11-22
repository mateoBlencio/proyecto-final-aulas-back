package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestAuditLabelProvider")
class RoomRequestAuditLabelProviderTest {

    @Mock
    private SubjectService subjectService;

    private RoomRequestAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RoomRequestAuditLabelProvider(subjectService);
    }

    @Test
    @DisplayName("entityType es RoomRequest")
    void entityType_isRoomRequest() {
        assertThat(provider.entityType()).isEqualTo(RoomRequest.class);
    }

    @Test
    @DisplayName("formato 'Solicitud de {docente}'")
    void labels_usesTeacherName() {
        Map<String, String> labels = provider.labels(List.of(
                new AuditedRecord("1", Map.of("teacherName", "Juan Pérez")),
                new AuditedRecord("2", Map.of("teacherName", "Ada Lovelace"))));

        assertThat(labels).containsOnly(
                Map.entry("1", "Solicitud de Juan Pérez"), Map.entry("2", "Solicitud de Ada Lovelace"));
    }

    @Test
    @DisplayName("sin nombre de docente (null o ausente) el registro no tiene etiqueta")
    void labels_missingTeacherName_hasNoLabel() {
        Map<String, Object> nullName = new HashMap<>();
        nullName.put("teacherName", null);

        assertThat(provider.labels(List.of(new AuditedRecord("1", nullName), new AuditedRecord("2", Map.of()))))
                .isEmpty();
    }

    @Test
    @DisplayName("summaryLabels: '{docente} ({materia})', con la materia en una sola consulta en lote que incluye desactivadas")
    void summaryLabels_teacherAndSubject() {
        when(subjectService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(new SubjectResponseDto(4L, 100, "Análisis Matemático I", null, null, true, null)));

        Map<String, String> summaries = provider.summaryLabels(List.of(
                new AuditedRecord("1", Map.of("teacherName", "Juan Pérez", "subjectId", 4L)),
                new AuditedRecord("2", Map.of("teacherName", "Ada Lovelace", "subjectId", 4L))));

        assertThat(summaries).containsOnly(
                Map.entry("1", "Juan Pérez (Análisis Matemático I)"),
                Map.entry("2", "Ada Lovelace (Análisis Matemático I)"));
    }

    @Test
    @DisplayName("summaryLabels: sin materia en la solicitud muestra solo el docente y no consulta materias")
    void summaryLabels_noSubject_onlyTeacher() {
        assertThat(provider.summaryLabels(List.of(new AuditedRecord("1", Map.of("teacherName", "Juan Pérez")))))
                .containsExactly(Map.entry("1", "Juan Pérez"));
        verifyNoInteractions(subjectService);
    }

    @Test
    @DisplayName("summaryLabels: con la materia que ya no se encuentra muestra solo el docente")
    void summaryLabels_subjectNotFound_onlyTeacher() {
        when(subjectService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());

        assertThat(provider.summaryLabels(List.of(
                new AuditedRecord("1", Map.of("teacherName", "Juan Pérez", "subjectId", 4L)))))
                .containsExactly(Map.entry("1", "Juan Pérez"));
    }

    @Test
    @DisplayName("summaryLabels: sin nombre de docente el registro no tiene resumen")
    void summaryLabels_noTeacher_hasNoSummary() {
        assertThat(provider.summaryLabels(List.of(new AuditedRecord("1", Map.of("subjectId", 4L))))).isEmpty();
    }
}
