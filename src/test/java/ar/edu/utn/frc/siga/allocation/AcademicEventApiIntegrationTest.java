package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Academic Event API (integración)")
class AcademicEventApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private AcademicEventRepository eventRepository;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private AcademicEventService academicEventService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /v1/events/recurring crea el evento y genera ocurrencias SCHEDULED con las fechas esperadas")
    void createRecurring_persistsEventAndOccurrencesWithExpectedDates() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        // dayOfWeek == startDate.getDayOfWeek(): nextOrSame(dayOfWeek) devuelve startDate exacto,
        // así la primera ocurrencia esperada es determinística.
        LocalDate startDate = LocalDate.now().plusDays(1);
        DayOfWeek dayOfWeek = startDate.getDayOfWeek();
        LocalDate endDate = startDate.plusWeeks(3);

        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, dayOfWeek, startDate, endDate, sc.subjectId(), sc.commissionId());

        MvcResult result = mockMvc.perform(post("/v1/events/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RECURRING"))
                .andExpect(jsonPath("$.dayOfWeek").value(dayOfWeek.name()))
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andReturn();

        Long eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(eventRepository.findById(eventId)).isPresent();

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        List<LocalDate> expectedDates = List.of(
                startDate, startDate.plusWeeks(1), startDate.plusWeeks(2), startDate.plusWeeks(3));
        assertThat(occurrences).extracting(Occurrence::getDate).containsExactlyInAnyOrderElementsOf(expectedDates);
        assertThat(occurrences).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED));
    }

    @Test
    @DisplayName("POST /v1/events/recurring con subjectId inexistente responde 404")
    void createRecurring_unknownSubject_returns404() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        long unknownSubjectId = 999_999_000L + IntegrationTestData.nextSeq();

        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY,
                LocalDate.now().plusDays(1), null, unknownSubjectId, sc.commissionId());

        mockMvc.perform(post("/v1/events/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("POST /v1/events/unique crea el evento y exactamente 1 ocurrencia")
    void createUnique_persistsSingleOccurrence() throws Exception {
        LocalDate date = LocalDate.now().plusDays(5);
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                20, LocalTime.of(10, 0), 60, date, "Evento único IT");

        MvcResult result = mockMvc.perform(post("/v1/events/unique")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("UNIQUE_EVENT"))
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andReturn();

        Long eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().getDate()).isEqualTo(date);
        assertThat(occurrences.getFirst().getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
    }

    @Test
    @DisplayName("findOrCreateRecurringEvent no duplica: segunda llamada idéntica reusa el evento existente")
    void findOrCreateRecurringEvent_doesNotDuplicate() {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate startDate = LocalDate.now().plusDays(1);
        DayOfWeek dayOfWeek = startDate.getDayOfWeek();

        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, dayOfWeek, startDate, null, sc.subjectId(), sc.commissionId());

        long before = eventRepository.count();

        FindOrCreateResult<AcademicEventResponseDto> first = academicEventService.findOrCreateRecurringEvent(dto);
        FindOrCreateResult<AcademicEventResponseDto> second = academicEventService.findOrCreateRecurringEvent(dto);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertThat(eventRepository.count()).isEqualTo(before + 1);
    }
}
