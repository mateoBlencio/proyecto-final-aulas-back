package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestItemAuditLabelProvider")
class RoomRequestItemAuditLabelProviderTest {

    @Mock
    private RoomRequestRepository requestRepository;
    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;

    private RoomRequestItemAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RoomRequestItemAuditLabelProvider(requestRepository, subjectService, commissionService);
    }

    private static AuditedRecord record(String id, Long requestId, Long commissionId, LocalDate date, String day) {
        Map<String, Object> values = new HashMap<>();
        values.put("request", requestId);
        values.put("commissionId", commissionId);
        values.put("date", date);
        values.put("dayOfWeek", day);
        return new AuditedRecord(id, values);
    }

    private void catalogs() {
        when(requestRepository.findAllById(any())).thenReturn(List.of(RoomRequest.builder().id(3L).subjectId(1L).build()));
        when(subjectService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(new SubjectResponseDto(1L, 100, "Análisis Matemático I", null, null, true, null)));
        when(commissionService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(new CommissionResponseDto(2L, "1K1", null, null)));
    }

    @Test
    @DisplayName("entityType es RoomRequestItem")
    void entityType_isRoomRequestItem() {
        assertThat(provider.entityType()).isEqualTo(RoomRequestItem.class);
    }

    @Test
    @DisplayName("pedido por fecha: '{materia} ({comisión}) · dd/MM/yyyy', con la materia tomada de la solicitud")
    void labels_dateItem() {
        catalogs();

        assertThat(provider.labels(List.of(record("7", 3L, 2L, LocalDate.of(2026, 3, 4), null))))
                .containsExactly(Map.entry("7", "Análisis Matemático I (1K1) · 04/03/2026"));
    }

    @Test
    @DisplayName("pedido por día de dictado: el día sale en español, no el nombre de la constante")
    void labels_dayOfWeekItem() {
        catalogs();

        String label = provider.labels(List.of(record("7", 3L, 2L, null, "MONDAY"))).get("7");

        assertThat(label).isEqualTo("Análisis Matemático I (1K1) · Lunes").doesNotContain("MONDAY");
    }

    @Test
    @DisplayName("sin materia ni comisión muestra solo cuándo; sin nada de eso no hay etiqueta")
    void labels_noIdentity_showsOnlyWhenOrNothing() {
        when(requestRepository.findAllById(any())).thenReturn(List.of(RoomRequest.builder().id(3L).build()));

        Map<String, String> labels = provider.labels(List.of(
                record("7", 3L, null, LocalDate.of(2026, 3, 4), null), record("8", 3L, null, null, null)));

        assertThat(labels).containsExactly(Map.entry("7", "04/03/2026"));
    }

    @Test
    @DisplayName("una consulta de solicitudes, una de materias y una de comisiones para todo el lote")
    void labels_severalRecords_oneBatchCallPerSource() {
        catalogs();

        provider.labels(List.of(record("7", 3L, 2L, LocalDate.of(2026, 3, 4), null),
                record("8", 3L, 2L, LocalDate.of(2026, 3, 5), null)));

        verify(requestRepository, times(1)).findAllById(any());
        verify(subjectService, times(1)).findByIdsIncludingDeactivated(any());
        verify(commissionService, times(1)).findByIdsIncludingDeactivated(any());
    }

    @Test
    @DisplayName("summaryLabels es solo materia y comisión, sin fecha ni día")
    void summaryLabels_isOnlySubjectAndCommission() {
        catalogs();

        assertThat(provider.summaryLabels(List.of(record("7", 3L, 2L, LocalDate.of(2026, 3, 4), null),
                record("8", 3L, 2L, null, "MONDAY"))))
                .containsOnly(Map.entry("7", "Análisis Matemático I (1K1)"),
                        Map.entry("8", "Análisis Matemático I (1K1)"));
    }

    @Test
    @DisplayName("sin ids de solicitud ni de comisión no consulta ningún servicio")
    void labels_noIds_queriesNothing() {
        assertThat(provider.labels(List.of(record("7", null, null, null, null)))).isEmpty();

        verifyNoInteractions(requestRepository, subjectService, commissionService);
    }
}
