package ar.edu.utn.frc.siga.preview;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.preview.dto.request.ReallocationSuggestionRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto.SuggestionStatus;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sugerencia automática de aula para reasignación (día puntual o rango) y su confirmación. */
@Import(IntegrationTestData.class)
@DisplayName("Sugerencia de reasignación (integración, solver real)")
class ReallocationSuggestionIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(9, 0);
    private static final int DURATION = 60;

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

    private Long createEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private Long createRangeEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate start, int weeks) {
        LocalDate end = start.plusWeeks(weeks - 1);
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, start.getDayOfWeek(), start, end, sc.subjectId(), sc.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private List<Occurrence> occurrencesOf(Long eventId) {
        return occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate))
                .toList();
    }

    private Occurrence occurrenceOf(Long eventId) {
        return occurrencesOf(eventId).getFirst();
    }

    private void allocateDirect(Occurrence occurrence, Long classroomId) {
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId()).classroomId(classroomId).source(AllocationSource.MANUAL).build());
    }

    /**
     * Bloquea, en cada fecha dada, todas las aulas que existen en la base al momento de llamar (no solo
     * las creadas por este test): otras clases de integración corridas antes en el mismo contenedor
     * dejan aulas libres en cualquier fecha futura no bloqueada explícitamente, y esas aulas serían
     * candidatas igual de válidas para el solver. Llamar esto antes de crear las aulas "propias" del test
     * es lo que garantiza que el solver elija determinísticamente entre ellas.
     */
    private void blockAllAvailableRooms(List<LocalDate> dates) {
        for (ClassroomResponseDto room : asFixtureUser(classroomService::findAllAvailable)) {
            for (LocalDate date : dates) {
                RecurringEvent blocker = eventRepository.save(RecurringEvent.builder()
                        .enrolled(1).startTime(START).duration(Duration.ofMinutes(DURATION))
                        .dayOfWeek(date.getDayOfWeek()).startDate(date).endDate(date).build());
                Occurrence occ = occurrenceRepository.save(Occurrence.builder()
                        .event(blocker).date(date).status(OccurrenceStatus.NEEDS_ROOM).build());
                allocationRepository.save(Allocation.builder()
                        .occurrenceId(occ.getId()).classroomId(room.id()).source(AllocationSource.MANUAL).build());
            }
        }
    }

    private void blockAllAvailableRooms(LocalDate date) {
        blockAllAvailableRooms(List.of(date));
    }

    private ReallocationSuggestionResponseDto suggest(ReallocationSuggestionRequestDto request) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/previews/reallocation-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ReallocationSuggestionResponseDto.class);
    }

    private void allocateOk(Long occurrenceId, Long classroomId) throws Exception {
        var dto = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    private void reallocatePut(Long occurrenceId, Long classroomId) throws Exception {
        var dto = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rango con dos aulas, una ocupada por otro evento en una fecha: sugiere la libre; el confirm persiste todo el rango con source AUTOMATIC")
    void suggestOverRange_oneRoomOccupiedOnOneDate_suggestsFreeRoom_confirmPersistsAutomatic() throws Exception {
        var sc = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusDays(500);
        List<LocalDate> dates = List.of(start, start.plusWeeks(1), start.plusWeeks(2));
        blockAllAvailableRooms(dates);
        var edificio = testData.edificio();
        Classroom roomA = testData.aula(edificio);
        Classroom roomB = testData.aula(edificio);

        Long eventId = createRangeEvent(sc, start, 3);
        List<Occurrence> occurrences = occurrencesOf(eventId);
        assertThat(occurrences).extracting(Occurrence::getDate).containsExactlyElementsOf(dates);

        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, occurrences.get(1).getDate());
        allocateDirect(occurrenceOf(foreignEventId), roomA.getId());

        ReallocationSuggestionResponseDto suggestion = suggest(new ReallocationSuggestionRequestDto(
                null, eventId, start, start.plusWeeks(2), null));

        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(suggestion.classroom().id()).isEqualTo(roomB.getId());
        assertThat(suggestion.suggestionId()).isNotBlank();

        mockMvc.perform(post("/v1/previews/reallocation-suggestion/{id}/confirm", suggestion.suggestionId()))
                .andExpect(status().isOk());

        for (Occurrence occurrence : occurrences) {
            Allocation persisted = allocationRepository.findByOccurrenceId(occurrence.getId()).orElseThrow();
            assertThat(persisted.getClassroomId()).isEqualTo(roomB.getId());
            assertThat(persisted.getSource()).isEqualTo(AllocationSource.AUTOMATIC);
        }
    }

    @Test
    @DisplayName("sin regresión del flujo manual: PUT /v1/allocations con el mismo target deja source MANUAL")
    void manualPut_stillPersistsWithSourceManual() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom room = testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(510);

        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        reallocatePut(occurrence.getId(), room.getId());

        Allocation persisted = allocationRepository.findByOccurrenceId(occurrence.getId()).orElseThrow();
        assertThat(persisted.getClassroomId()).isEqualTo(room.getId());
        assertThat(persisted.getSource()).isEqualTo(AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("segundo confirm del mismo suggestionId responde 410")
    void confirm_secondAttemptOnSameId_returns410() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(520);
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        ReallocationSuggestionResponseDto suggestion = suggest(
                new ReallocationSuggestionRequestDto(List.of(occurrence.getId()), null, null, null, null));
        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.SUGGESTED);

        mockMvc.perform(post("/v1/previews/reallocation-suggestion/{id}/confirm", suggestion.suggestionId()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/previews/reallocation-suggestion/{id}/confirm", suggestion.suggestionId()))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("conflicto inyectado entre sugerencia y confirm: confirm responde 409, no persiste nada, y un nuevo confirm con el mismo id da 410")
    void confirm_conflictInjectedBetweenSuggestionAndConfirm_returns409_thenGone() throws Exception {
        var sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(530);
        blockAllAvailableRooms(date);
        Classroom onlyRoom = testData.aula(testData.edificio());
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        ReallocationSuggestionResponseDto suggestion = suggest(
                new ReallocationSuggestionRequestDto(List.of(occurrence.getId()), null, null, null, null));
        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(suggestion.classroom().id()).isEqualTo(onlyRoom.getId());

        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, date);
        allocateDirect(occurrenceOf(foreignEventId), onlyRoom.getId());

        mockMvc.perform(post("/v1/previews/reallocation-suggestion/{id}/confirm", suggestion.suggestionId()))
                .andExpect(status().isConflict());

        assertThat(allocationRepository.findByOccurrenceId(occurrence.getId())).isEmpty();

        mockMvc.perform(post("/v1/previews/reallocation-suggestion/{id}/confirm", suggestion.suggestionId()))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("todas las aulas disponibles bloqueadas en la fecha: NO_ROOM_AVAILABLE, classroom y suggestionId null")
    void suggest_allRoomsBlocked_returnsNoRoomAvailable() throws Exception {
        var sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(540);
        blockAllAvailableRooms(date);
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        ReallocationSuggestionResponseDto suggestion = suggest(
                new ReallocationSuggestionRequestDto(List.of(occurrence.getId()), null, null, null, null));

        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.NO_ROOM_AVAILABLE);
        assertThat(suggestion.classroom()).isNull();
        assertThat(suggestion.suggestionId()).isNull();
    }

    @Test
    @DisplayName("aula actual del evento en excludedClassroomIds: la sugerencia devuelve otra aula")
    void suggest_currentRoomExcluded_suggestsAnotherRoom() throws Exception {
        var sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(550);
        blockAllAvailableRooms(date);
        var edificio = testData.edificio();
        Classroom currentRoom = testData.aula(edificio);
        Classroom otherRoom = testData.aula(edificio);
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);
        allocateOk(occurrence.getId(), currentRoom.getId());

        ReallocationSuggestionResponseDto suggestion = suggest(new ReallocationSuggestionRequestDto(
                List.of(occurrence.getId()), null, null, null, List.of(currentRoom.getId())));

        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(suggestion.classroom().id()).isEqualTo(otherRoom.getId());
    }

    @Test
    @DisplayName("la sugerencia sola no escribe: la cantidad de allocations es la misma antes y después")
    void suggest_alone_doesNotWriteAllocations() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        testData.aula(edificio);
        testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(560);
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        long before = allocationRepository.count();

        ReallocationSuggestionResponseDto suggestion = suggest(
                new ReallocationSuggestionRequestDto(List.of(occurrence.getId()), null, null, null, null));

        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(allocationRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("suggest: from anterior a hoy responde 400")
    void suggest_pastFrom_returns400() throws Exception {
        var sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(570);
        Long eventId = createEvent(sc, date);

        mockMvc.perform(post("/v1/previews/reallocation-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReallocationSuggestionRequestDto(
                                null, eventId, LocalDate.now().minusDays(1), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("suggest: ocurrencia ya pasada responde 409")
    void suggest_pastOccurrence_returns409() throws Exception {
        LocalDate pastDate = LocalDate.now().minusMonths(1);
        var sc = testData.materiaYComision();
        RecurringEvent pastEvent = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(pastDate.getDayOfWeek()).startDate(pastDate).endDate(pastDate)
                .subjectId(sc.subjectId()).commissionId(sc.commissionId()).build());
        Occurrence pastOccurrence = occurrenceRepository.save(Occurrence.builder()
                .event(pastEvent).date(pastDate).status(OccurrenceStatus.NEEDS_ROOM).build());

        mockMvc.perform(post("/v1/previews/reallocation-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReallocationSuggestionRequestDto(
                                List.of(pastOccurrence.getId()), null, null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("un usuario CONSULTA (sin los permisos) recibe 403 en los dos endpoints")
    void consultaRole_isForbidden_onBothEndpoints() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(580);
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        MockMvc consultaMockMvc = mockMvcAs("consulta-reallocation@frc.utn.edu.ar", SystemRole.CONSULTA);

        consultaMockMvc.perform(post("/v1/previews/reallocation-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReallocationSuggestionRequestDto(
                                List.of(occurrence.getId()), null, null, null, null))))
                .andExpect(status().isForbidden());

        consultaMockMvc.perform(post("/v1/previews/reallocation-suggestion/{id}/confirm", "sug_no_existe"))
                .andExpect(status().isForbidden());
    }
}
