package ar.edu.utn.frc.siga.preview;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduce la forma del caso real del bug del aula previa: dos eventos con aula previa X y tres
 * eventos de la misma propuesta que compiten por X en horarios solapados, con X como única aula libre.
 * Sin la constraint PriorRoomRespected el solver asigna X a los tres competidores (tres asignaciones
 * valen más que dos), deja sin aula a los dueños y el composer les devuelve X: propuesta con choques
 * internos y confirm 409.
 */
@Import(IntegrationTestData.class)
@DisplayName("Preview con aula previa disputada (integración, solver real)")
class PriorRoomPreviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createEvent(LocalDate date, LocalTime start, int minutes) {
        var sc = testData.materiaYComision();
        return academicEventService.createRecurringEvent(new CreateRecurringEventRequestDto(
                30, start, minutes, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId())).id();
    }

    private void allocateDirect(Long eventId, Long classroomId) {
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId()).classroomId(classroomId).source(AllocationSource.MANUAL).build());
    }

    private void blockAllDayExcept(Long freeRoomId, LocalDate date) {
        for (ClassroomResponseDto room : asFixtureUser(classroomService::findAllAvailable)) {
            if (room.id().equals(freeRoomId)) {
                continue;
            }
            UniqueEvent blocker = eventRepository.save(UniqueEvent.builder()
                    .enrolled(1).startTime(LocalTime.MIDNIGHT).duration(Duration.ofMinutes(23 * 60 + 59))
                    .date(date).description("blocker").kind(UniqueEventKind.OTRO).build());
            Occurrence occ = occurrenceRepository.save(Occurrence.builder()
                    .event(blocker).date(date).status(OccurrenceStatus.NEEDS_ROOM).build());
            allocationRepository.save(Allocation.builder()
                    .occurrenceId(occ.getId()).classroomId(room.id()).source(AllocationSource.MANUAL).build());
        }
    }

    private static boolean overlap(PreviewItemDto a, PreviewItemDto b) {
        LocalTime aEnd = a.event().startTime().plusMinutes(a.event().durationMinutes());
        LocalTime bEnd = b.event().startTime().plusMinutes(b.event().durationMinutes());
        boolean sharesDate = a.occurrenceDates().stream().anyMatch(b.occurrenceDates()::contains);
        return sharesDate && a.event().startTime().isBefore(bEnd) && b.event().startTime().isBefore(aEnd);
    }

    private static List<String> internalClashes(PreviewResponseDto preview) {
        List<String> clashes = new ArrayList<>();
        List<PreviewItemDto> items = preview.allocations();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                PreviewItemDto a = items.get(i);
                PreviewItemDto b = items.get(j);
                if (a.classroom().id().equals(b.classroom().id()) && overlap(a, b)) {
                    clashes.add(a.event().id() + " vs " + b.event().id() + " en aula " + a.classroom().id());
                }
            }
        }
        return clashes;
    }

    @Test
    @DisplayName("dueños del aula previa no la pierden ante competidores: sin choques internos y confirm sin 409")
    void contestedPriorRoom_proposalHasNoInternalClashes_andConfirmSucceeds() throws Exception {
        LocalDate wednesday = LocalDate.now().plusDays(900).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        LocalDate thursday = wednesday.plusDays(1);
        Classroom roomX = testData.aula(testData.edificio());
        blockAllDayExcept(roomX.getId(), wednesday);
        blockAllDayExcept(roomX.getId(), thursday);

        Long ownerThursday = createEvent(thursday, LocalTime.of(18, 15), 145);
        Long ownerWednesday = createEvent(wednesday, LocalTime.of(19, 55), 145);
        allocateDirect(ownerThursday, roomX.getId());
        allocateDirect(ownerWednesday, roomX.getId());
        Long competitorWednesday = createEvent(wednesday, LocalTime.of(19, 55), 190);
        Long competitorThursdayEarly = createEvent(thursday, LocalTime.of(17, 20), 145);
        Long competitorThursdayLate = createEvent(thursday, LocalTime.of(19, 55), 190);

        MvcResult previewResult = mockMvc.perform(post("/v1/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PreviewRequestDto(
                                List.of(ownerThursday, ownerWednesday, competitorWednesday,
                                        competitorThursdayEarly, competitorThursdayLate),
                                null, null, 3))))
                .andExpect(status().isOk())
                .andReturn();
        PreviewResponseDto preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), PreviewResponseDto.class);

        assertThat(internalClashes(preview)).isEmpty();
        assertThat(preview.allocations()).filteredOn(i -> i.event().id().equals(ownerThursday)
                        || i.event().id().equals(ownerWednesday))
                .hasSize(2)
                .allSatisfy(item -> assertThat(item.classroom().id()).isEqualTo(roomX.getId()));

        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(
                                preview.allocations().stream()
                                        .map(i -> new PreviewAllocationDto(
                                                i.event().id(), i.roomSlot(), i.classroom().id()))
                                        .toList()))))
                .andExpect(status().isOk());
    }
}
