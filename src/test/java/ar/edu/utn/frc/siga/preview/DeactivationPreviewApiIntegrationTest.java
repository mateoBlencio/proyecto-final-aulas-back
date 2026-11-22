package ar.edu.utn.frc.siga.preview;

import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.AbstractDeactivationIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Preview y confirmación sobre clases liberadas por baja de aula (integración)")
class DeactivationPreviewApiIntegrationTest extends AbstractDeactivationIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("un evento cuya unica fecha pendiente fue liberada por baja entra al preview y la confirmacion la asigna")
    void previewIncluyeLaLiberadaPorBaja() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        testData.aula(testData.edificio());
        Long occurrenceId = allocatedOccurrence(classroom, futureMonday());
        Long eventId = occurrence(occurrenceId).getEvent().getId();
        deactivateClassroom(classroom).andExpect(status().isOk());

        PreviewResponseDto preview = autoPreview(eventId);

        assertThat(preview.allocations()).hasSize(1);
        Long proposedRoom = preview.allocations().getFirst().classroom().id();
        assertThat(proposedRoom).isNotEqualTo(classroom.getId());
        confirm(eventId, preview, proposedRoom);
        Occurrence reassigned = occurrence(occurrenceId);
        assertThat(reassigned.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(reassigned.getReleaseReason()).isNull();
        assertThat(allocationOf(occurrenceId)).hasValueSatisfying(
                a -> assertThat(a.getClassroomId()).isEqualTo(proposedRoom));
    }

    @Test
    @DisplayName("una fecha CANCELLED del mismo evento no entra: queda liberada y sin aula tras confirmar")
    void laCancelledNoEntra() throws Exception {
        LocalDate first = futureMonday();
        var sc = testData.materiaYComision();
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, first, first.plusWeeks(1), 30);
        List<Occurrence> dates = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        Long byDeactivation = dates.get(0).getId();
        Long cancelled = dates.get(1).getId();
        Classroom dropped = testData.aula(testData.edificio());
        Classroom other = testData.aula(testData.edificio());
        testData.aula(testData.edificio());
        allocate(byDeactivation, dropped).andExpect(status().isCreated());
        allocate(cancelled, other).andExpect(status().isCreated());
        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + cancelled + "]}],\"reason\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        deactivateClassroom(dropped).andExpect(status().isOk());

        PreviewResponseDto preview = autoPreview(eventId);
        Long proposedRoom = preview.allocations().getFirst().classroom().id();
        confirm(eventId, preview, proposedRoom);

        assertThat(allocationOf(byDeactivation)).hasValueSatisfying(
                a -> assertThat(a.getClassroomId()).isEqualTo(proposedRoom));
        Occurrence stillCancelled = occurrence(cancelled);
        assertThat(stillCancelled.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(stillCancelled.getReleaseReason()).isEqualTo(ReleaseReason.CANCELLED);
        assertThat(allocationOf(cancelled)).isEmpty();
    }

    private PreviewResponseDto autoPreview(Long eventId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/previews").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PreviewRequestDto(List.of(eventId), null, null, 2))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PreviewResponseDto.class);
    }

    private void confirm(Long eventId, PreviewResponseDto preview, Long classroomId) throws Exception {
        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, null, classroomId))))))
                .andExpect(status().isOk());
    }
}
