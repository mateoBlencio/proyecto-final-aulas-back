package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Liberación con motivo (integración): nadie pisa una ocurrencia liberada")
class ReleaseWithReasonApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private ObjectMapper objectMapper;

    private record Fixture(Long eventId, Long firstOccurrenceId, Long releasedOccurrenceId, Classroom original,
                           Classroom other) {}

    /** Evento semanal de dos fechas, ambas con aula; la segunda liberada por la API. */
    private Fixture eventWithSecondOccurrenceReleased() throws Exception {
        var sc = testData.materiaYComision();
        var building = testData.edificio();
        Classroom original = testData.aula(building);
        Classroom other = testData.aula(building);
        LocalDate monday = LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, monday, monday.plusWeeks(1), 30);
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        Long first = occurrences.get(0).getId();
        Long second = occurrences.get(1).getId();

        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"eventId\":" + eventId + ",\"classroomId\":" + original.getId() + "}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + second + "]}],\"reason\":\"VIRTUAL_CLASS\","
                                + "\"note\":\"cursado virtual\"}"))
                .andExpect(status().isOk());
        return new Fixture(eventId, first, second, original, other);
    }

    private void assertStillReleasedWithoutRoom(Fixture f) {
        Occurrence released = occurrenceRepository.findById(f.releasedOccurrenceId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(released.getReleaseReason()).isEqualTo(ReleaseReason.VIRTUAL_CLASS);
        assertThat(allocationRepository.findByOccurrenceId(f.releasedOccurrenceId())).isEmpty();
    }

    @Test
    @DisplayName("liberar borra la asignacion, guarda motivo, nota y aula anterior; revertir vuelve a NEEDS_ROOM")
    void releaseAndCancel() throws Exception {
        Fixture f = eventWithSecondOccurrenceReleased();

        Occurrence released = occurrenceRepository.findById(f.releasedOccurrenceId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(released.getReleaseReason()).isEqualTo(ReleaseReason.VIRTUAL_CLASS);
        assertThat(released.getReleaseNote()).isEqualTo("cursado virtual");
        assertThat(released.getPreviousClassroomId()).isEqualTo(f.original().getId());
        assertThat(allocationRepository.findByOccurrenceId(f.releasedOccurrenceId())).isEmpty();
        assertThat(allocationRepository.findByOccurrenceId(f.firstOccurrenceId())).isPresent();

        mockMvc.perform(delete("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + f.releasedOccurrenceId() + "]}]}"))
                .andExpect(status().isOk());

        Occurrence reverted = occurrenceRepository.findById(f.releasedOccurrenceId()).orElseThrow();
        assertThat(reverted.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(reverted.getReleaseReason()).isNull();
        assertThat(reverted.getReleaseNote()).isNull();
        assertThat(reverted.getPreviousClassroomId()).isEqualTo(f.original().getId());
    }

    @Test
    @DisplayName("PUT /v1/allocations sobre el evento entero no le pone aula a la liberada")
    void reallocateSkipsReleased() throws Exception {
        Fixture f = eventWithSecondOccurrenceReleased();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"eventId\":" + f.eventId() + ",\"classroomId\":" + f.other().getId() + "}]}"))
                .andExpect(status().isOk());

        assertThat(allocationRepository.findByOccurrenceId(f.firstOccurrenceId()).orElseThrow().getClassroomId())
                .isEqualTo(f.other().getId());
        assertStillReleasedWithoutRoom(f);
    }

    @Test
    @DisplayName("syncFromSysacad sobre el evento no le pone aula a la liberada")
    void sysacadSyncSkipsReleased() throws Exception {
        Fixture f = eventWithSecondOccurrenceReleased();

        int affected = SystemScope.call(() -> allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(f.eventId()), f.other().getId()))));

        assertThat(affected).isZero();
        assertThat(allocationRepository.findByOccurrenceId(f.firstOccurrenceId()).orElseThrow().getSource())
                .isNotEqualTo(AllocationSource.SYSACAD);
        assertStillReleasedWithoutRoom(f);
    }

    @Test
    @DisplayName("confirm de preview sobre el evento no le pone aula a la liberada")
    void previewConfirmSkipsReleased() throws Exception {
        Fixture f = eventWithSecondOccurrenceReleased();

        MvcResult previewResult = mockMvc.perform(post("/v1/previews").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PreviewRequestDto(List.of(f.eventId()), null, null, 2))))
                .andExpect(status().isOk()).andReturn();
        PreviewResponseDto preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), PreviewResponseDto.class);
        Long proposedRoom = preview.allocations().getFirst().classroom().id();

        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(
                                List.of(new PreviewAllocationDto(f.eventId(), null, proposedRoom))))))
                .andExpect(status().isOk());

        assertThat(allocationRepository.findByOccurrenceId(f.firstOccurrenceId()).orElseThrow().getClassroomId())
                .isEqualTo(proposedRoom);
        assertStillReleasedWithoutRoom(f);
    }
}
