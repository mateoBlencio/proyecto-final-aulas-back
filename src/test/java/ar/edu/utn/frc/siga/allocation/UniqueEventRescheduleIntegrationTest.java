package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edición de un evento único que cambia fecha u horario: allocation revalida el aula en la misma
 * transacción (409/400 hacen rollback de la edición) y roomrequest sincroniza la fecha de los pedidos.
 * El margen de solape por defecto es de 40 minutos.
 */
@Import(IntegrationTestData.class)
@DisplayName("Reprogramación de evento único (integración)")
class UniqueEventRescheduleIntegrationTest extends AbstractIntegrationTest {

    private static final int DURATION = 60;
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private UniqueEventRepository uniqueEventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private record Seeded(Long eventId, Long occurrenceId, IntegrationTestData.SubjectAndCommission academic) {
    }

    private Seeded seedUnique(IntegrationTestData.SubjectAndCommission academic, LocalDate date, LocalTime start) {
        var dto = new CreateUniqueEventRequestDto(UniqueEventKind.EXAMEN_FINAL, academic.subjectId(),
                academic.commissionId(), date, start, DURATION, 20, null, null, null, null, null, null);
        Long eventId = academicEventService.createUniqueEvent(dto).id();
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        return new Seeded(eventId, occurrenceId, academic);
    }

    private void allocate(Long occurrenceId, Long classroomId) throws Exception {
        var dto = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    private String updateBody(Seeded seeded, LocalDate date, LocalTime start, String observation) throws Exception {
        var dto = new UpdateUniqueEventRequestDto(UniqueEventKind.EXAMEN_FINAL, seeded.academic().subjectId(),
                seeded.academic().commissionId(), date, start, DURATION, 20, null, null, null, null, null, null,
                observation);
        return objectMapper.writeValueAsString(dto);
    }

    private Allocation allocationOf(Long occurrenceId) {
        return allocationRepository.findByOccurrenceIdIn(List.of(occurrenceId)).getFirst();
    }

    private void assertUnchanged(Seeded seeded, LocalDate originalDate, LocalTime originalStart, Long classroomId) {
        UniqueEvent event = uniqueEventRepository.findById(seeded.eventId()).orElseThrow();
        assertThat(event.getDate()).isEqualTo(originalDate);
        assertThat(event.getStartTime()).isEqualTo(originalStart);
        Occurrence occurrence = occurrenceRepository.findById(seeded.occurrenceId()).orElseThrow();
        assertThat(occurrence.getDate()).isEqualTo(originalDate);
        assertThat(allocationOf(seeded.occurrenceId()).getClassroomId()).isEqualTo(classroomId);
    }

    @Test
    @DisplayName("PUT /v1/events/unique/{id}: mover a una franja que solapa más que el margen en la misma aula responde 409 y la fecha en base no cambia")
    void solapeMayorAlMargen_409YRollback() throws Exception {
        var academic = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate busyDate = LocalDate.now().plusDays(20);
        LocalDate originalDate = LocalDate.now().plusDays(21);
        Seeded busy = seedUnique(academic, busyDate, TEN);
        Seeded moved = seedUnique(academic, originalDate, LocalTime.of(14, 0));
        allocate(busy.occurrenceId(), aula.getId());
        allocate(moved.occurrenceId(), aula.getId());

        mockMvc.perform(put("/v1/events/unique/{id}", moved.eventId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(moved, busyDate, TEN, "intento")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reallocation conflict"));

        assertUnchanged(moved, originalDate, LocalTime.of(14, 0), aula.getId());
        assertThat(allocationOf(moved.occurrenceId()).getObservation()).isNull();
    }

    @Test
    @DisplayName("PUT /v1/events/unique/{id}: solape de 30 minutos (dentro del margen) sin observation responde 400 y la fecha en base no cambia")
    void solapeToleradoSinObservacion_400YRollback() throws Exception {
        var academic = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate busyDate = LocalDate.now().plusDays(22);
        LocalDate originalDate = LocalDate.now().plusDays(23);
        Seeded busy = seedUnique(academic, busyDate, TEN); // 10:00-11:00
        Seeded moved = seedUnique(academic, originalDate, LocalTime.of(14, 0));
        allocate(busy.occurrenceId(), aula.getId());
        allocate(moved.occurrenceId(), aula.getId());

        mockMvc.perform(put("/v1/events/unique/{id}", moved.eventId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(moved, busyDate, LocalTime.of(10, 30), null))) // 10:30-11:30
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Observation required"))
                .andExpect(jsonPath("$.overlaps[0].overlapMinutes").value(30));

        assertUnchanged(moved, originalDate, LocalTime.of(14, 0), aula.getId());
    }

    @Test
    @DisplayName("PUT /v1/events/unique/{id}: solape de 30 minutos con observation responde 200, mueve la fecha y guarda la observación en la asignación sin cambiar su source")
    void solapeToleradoConObservacion_200() throws Exception {
        var academic = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate busyDate = LocalDate.now().plusDays(24);
        LocalDate originalDate = LocalDate.now().plusDays(25);
        Seeded busy = seedUnique(academic, busyDate, TEN);
        Seeded moved = seedUnique(academic, originalDate, LocalTime.of(14, 0));
        allocate(busy.occurrenceId(), aula.getId());
        allocate(moved.occurrenceId(), aula.getId());
        assertThat(allocationOf(moved.occurrenceId()).getSource()).isEqualTo(AllocationSource.MANUAL);

        mockMvc.perform(put("/v1/events/unique/{id}", moved.eventId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(moved, busyDate, LocalTime.of(10, 30), "autorizado por secretaría")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(busyDate.toString()));

        UniqueEvent event = uniqueEventRepository.findById(moved.eventId()).orElseThrow();
        assertThat(event.getDate()).isEqualTo(busyDate);
        assertThat(event.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(occurrenceRepository.findById(moved.occurrenceId()).orElseThrow().getDate()).isEqualTo(busyDate);
        Allocation allocation = allocationOf(moved.occurrenceId());
        assertThat(allocation.getClassroomId()).isEqualTo(aula.getId());
        assertThat(allocation.getObservation()).isEqualTo("autorizado por secretaría");
        assertThat(allocation.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(allocationOf(busy.occurrenceId()).getObservation()).isNull();
    }

    @Test
    @DisplayName("PUT /v1/events/unique/{id}: a una fecha libre, la fecha de los ítems de pedido que apuntan a la ocurrencia se actualiza")
    void fechaLibre_actualizaLaFechaDelItemDePedido() throws Exception {
        var academic = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate originalDate = LocalDate.now().plusDays(26);
        LocalDate newDate = LocalDate.now().plusDays(27);
        Seeded moved = seedUnique(academic, originalDate, TEN);
        allocate(moved.occurrenceId(), aula.getId());
        Long itemId = seedRequestItemOn(academic, moved.occurrenceId(), aula.getId(), originalDate);
        assertThat(itemRepository.findById(itemId).orElseThrow().getDate()).isEqualTo(originalDate);

        mockMvc.perform(put("/v1/events/unique/{id}", moved.eventId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(moved, newDate, TEN, null)))
                .andExpect(status().isOk());

        assertThat(itemRepository.findById(itemId).orElseThrow().getDate()).isEqualTo(newDate);
        assertThat(uniqueEventRepository.findById(moved.eventId()).orElseThrow().getDate()).isEqualTo(newDate);
    }

    @Test
    @DisplayName("PUT /v1/events/unique/{id}: sin cambio de fecha, hora ni duración no se sincroniza la fecha del ítem de pedido")
    void sinCambioDeHorario_noTocaElItem() throws Exception {
        var academic = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(28);
        Seeded seeded = seedUnique(academic, date, TEN);
        allocate(seeded.occurrenceId(), aula.getId());
        Long itemId = seedRequestItemOn(academic, seeded.occurrenceId(), aula.getId(), date.plusDays(3));

        mockMvc.perform(put("/v1/events/unique/{id}", seeded.eventId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(seeded, date, TEN, null)))
                .andExpect(status().isOk());

        assertThat(itemRepository.findById(itemId).orElseThrow().getDate()).isEqualTo(date.plusDays(3));
    }

    @Test
    @DisplayName("PUT /v1/events/unique/{id}: fecha y hora nuevas en el pasado responde 409 y no cambia la fecha del evento")
    void fechaNuevaPasada_409() throws Exception {
        var academic = testData.materiaYComision();
        LocalDate originalDate = LocalDate.now().plusDays(29);
        Seeded seeded = seedUnique(academic, originalDate, TEN);

        mockMvc.perform(put("/v1/events/unique/{id}", seeded.eventId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(seeded, LocalDate.now().minusDays(1), TEN, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Occurrence already past"));

        assertThat(uniqueEventRepository.findById(seeded.eventId()).orElseThrow().getDate()).isEqualTo(originalDate);
        assertThat(occurrenceRepository.findById(seeded.occurrenceId()).orElseThrow().getDate())
                .isEqualTo(originalDate);
    }

    @Test
    @DisplayName("POST /v1/events/recurring con fecha de fin anterior a la de inicio responde 400 y no crea el evento")
    void createRecurring_finAnteriorAInicio_400() throws Exception {
        var academic = testData.materiaYComision();
        LocalDate startDate = LocalDate.now().plusDays(10);
        var dto = new CreateRecurringEventRequestDto(30, LocalTime.of(8, 0), 90, startDate.getDayOfWeek(),
                startDate, startDate.minusDays(1), academic.subjectId(), academic.commissionId());

        mockMvc.perform(post("/v1/events/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/events/recurring con horario fuera de la ventana permitida responde 400")
    void createRecurring_horarioFueraDeVentana_400() throws Exception {
        var academic = testData.materiaYComision();
        LocalDate startDate = LocalDate.now().plusDays(10);
        var dto = new CreateRecurringEventRequestDto(30, LocalTime.of(22, 30), 90, DayOfWeek.MONDAY,
                startDate, startDate.plusWeeks(2), academic.subjectId(), academic.commissionId());

        mockMvc.perform(post("/v1/events/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid event schedule"));
    }

    private Long seedRequestItemOn(IntegrationTestData.SubjectAndCommission academic, Long occurrenceId,
                                   Long classroomId, LocalDate itemDate) {
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.FINAL_EXAM, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), itemDate,
                RoomRequestStatus.NEW);
        item.assignClassrooms(List.of(classroomId), List.of(List.of(occurrenceId)), List.of(), Map.of());
        roomRequestRepository.save(request);
        return item.getId();
    }
}
