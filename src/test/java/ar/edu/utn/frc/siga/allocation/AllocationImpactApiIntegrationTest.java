package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Allocation impact API (integración)")
class AllocationImpactApiIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final int DURATION = 90;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ObjectMapper objectMapper;

    // ---------- helpers ----------

    private Long seedWeeklyEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate start, int weeks) {
        var dto = new CreateRecurringEventRequestDto(30, START, DURATION, start.getDayOfWeek(),
                start, start.plusWeeks(weeks - 1L), sc.subjectId(), sc.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private Occurrence occurrenceOn(Long eventId, LocalDate date) {
        return occurrenceRepository.findByEvent_Id(eventId).stream()
                .filter(o -> o.getDate().equals(date))
                .findFirst().orElseThrow();
    }

    private static AllocationBatchRequestDto byRange(Long eventId, LocalDate from, LocalDate to, Long classroomId) {
        return new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(null, eventId, from, to, classroomId)), null);
    }

    private static AllocationBatchRequestDto byOccurrence(Long occurrenceId, Long classroomId) {
        return new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
    }

    private void allocate(AllocationBatchRequestDto body) throws Exception {
        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions impact(AllocationBatchRequestDto body) throws Exception {
        return mockMvc.perform(post("/v1/allocations/impact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Long classroomOf(Long occurrenceId) {
        return allocationRepository.findByOccurrenceIdIn(List.of(occurrenceId)).stream()
                .map(Allocation::getClassroomId).findFirst().orElse(null);
    }

    // ---------- tests ----------

    @Test
    @DisplayName("Rango sin choques: informa el total de clases que toca y no bloquea ninguna")
    void impactRangoLimpio() throws Exception {
        var sc = testData.materiaYComision();
        Classroom destino = testData.aula(testData.edificio());
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 4);

        impact(byRange(eventId, start, start.plusWeeks(3), destino.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClasses").value(4))
                .andExpect(jsonPath("$.movableClasses").value(4))
                .andExpect(jsonPath("$.blockedClasses").value(0))
                .andExpect(jsonPath("$.conflicts").isEmpty())
                .andExpect(jsonPath("$.occurrences.length()").value(4))
                .andExpect(jsonPath("$.occurrences[0].requestedClassroomId").value(destino.getId()))
                .andExpect(jsonPath("$.occurrences[0].currentClassroomId").doesNotExist())
                .andExpect(jsonPath("$.occurrences[0].blocked").value(false));
    }

    @Test
    @DisplayName("El rango recortado por 'to' cuenta solo las clases de adentro del rango")
    void impactRespetaElRango() throws Exception {
        var sc = testData.materiaYComision();
        Classroom destino = testData.aula(testData.edificio());
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 6);

        impact(byRange(eventId, start, start.plusWeeks(1), destino.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClasses").value(2));
    }

    @Test
    @DisplayName("Choque con una asignación existente: lo informa como dato con 200, no como error")
    void impactChoqueConAsignacionExistente() throws Exception {
        var edificio = testData.edificio();
        Classroom origen = testData.aula(edificio);
        Classroom destino = testData.aula(edificio);
        Classroom libre = testData.aula(edificio);

        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate fechaDelChoque = start.plusWeeks(1);

        // La materia que quiero mover, ya cursando en 'origen'.
        Long eventId = seedWeeklyEvent(testData.materiaYComision(), start, 3);
        allocate(new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(null, eventId, null, null, origen.getId())), null));

        // Otra materia que ocupa 'destino' justo en la fecha del medio, en la misma franja.
        Long bloqueanteId = seedWeeklyEvent(testData.materiaYComision(), fechaDelChoque, 1);
        Occurrence ocupante = occurrenceOn(bloqueanteId, fechaDelChoque);
        allocate(byOccurrence(ocupante.getId(), destino.getId()));

        Occurrence propiaBloqueada = occurrenceOn(eventId, fechaDelChoque);
        Long allocationBloqueante = allocationRepository.findByOccurrenceIdIn(List.of(ocupante.getId()))
                .getFirst().getId();

        impact(byRange(eventId, start, start.plusWeeks(2), destino.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClasses").value(3))
                .andExpect(jsonPath("$.movableClasses").value(2))
                .andExpect(jsonPath("$.blockedClasses").value(1))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].occurrenceId").value(propiaBloqueada.getId()))
                .andExpect(jsonPath("$.conflicts[0].requestedClassroomId").value(destino.getId()))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.kind").value("EXISTING_ALLOCATION"))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.eventId").value(bloqueanteId))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.occurrenceId").value(ocupante.getId()))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.allocationId").value(allocationBloqueante))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.eventType").value("RECURRING"))
                // el aula pedida está ocupada, así que no puede ofrecerse como alternativa
                // (jsonPath devuelve los id como Integer; comparo en int para no fallar por Long vs Integer)
                .andExpect(jsonPath("$.conflicts[0].alternativeClassrooms[*].id", not(hasItem(destino.getId().intValue()))))
                .andExpect(jsonPath("$.conflicts[0].alternativeClassrooms[*].id", hasItem(libre.getId().intValue())));
    }

    @Test
    @DisplayName("El aula que libera el propio movimiento vuelve a ofrecerse como alternativa")
    void impactOfreceElAulaQueSeLibera() throws Exception {
        var edificio = testData.edificio();
        Classroom origen = testData.aula(edificio);
        Classroom destino = testData.aula(edificio);

        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate fechaDelChoque = start.plusWeeks(1);

        Long eventId = seedWeeklyEvent(testData.materiaYComision(), start, 3);
        allocate(new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(null, eventId, null, null, origen.getId())), null));

        Long bloqueanteId = seedWeeklyEvent(testData.materiaYComision(), fechaDelChoque, 1);
        allocate(byOccurrence(occurrenceOn(bloqueanteId, fechaDelChoque).getId(), destino.getId()));

        // 'origen' queda libre porque justamente lo estoy vaciando con este movimiento.
        impact(byRange(eventId, start, start.plusWeeks(2), destino.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts[0].alternativeClassrooms[*].id", hasItem(origen.getId().intValue())));
    }

    @Test
    @DisplayName("El impact no escribe nada: las aulas quedan como estaban")
    void impactNoEscribeNada() throws Exception {
        var edificio = testData.edificio();
        Classroom origen = testData.aula(edificio);
        Classroom destino = testData.aula(edificio);
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(testData.materiaYComision(), start, 3);
        allocate(new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(null, eventId, null, null, origen.getId())), null));

        impact(byRange(eventId, start, start.plusWeeks(2), destino.getId()))
                .andExpect(status().isOk());

        for (Occurrence o : occurrenceRepository.findByEvent_Id(eventId)) {
            assertThat(classroomOf(o.getId())).isEqualTo(origen.getId());
        }
    }

    @Test
    @DisplayName("Dos items del mismo lote que se pisan se informan como SAME_BATCH, sin asignación bloqueante")
    void impactChoqueDentroDelMismoLote() throws Exception {
        Classroom destino = testData.aula(testData.edificio());
        LocalDate fecha = LocalDate.now().plusDays(7);

        Long unoId = seedWeeklyEvent(testData.materiaYComision(), fecha, 1);
        Long otroId = seedWeeklyEvent(testData.materiaYComision(), fecha, 1);

        var lote = new AllocationBatchRequestDto(List.of(
                new AllocationItemRequestDto(null, unoId, fecha, fecha, destino.getId()),
                new AllocationItemRequestDto(null, otroId, fecha, fecha, destino.getId())), null);

        impact(lote)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClasses").value(2))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.kind").value("SAME_BATCH"))
                .andExpect(jsonPath("$.conflicts[0].blockedBy.allocationId").doesNotExist());
    }

    @Test
    @DisplayName("Un rango que arranca en el pasado sigue siendo 400, no un impacto vacío")
    void impactDesdeElPasadoEs400() throws Exception {
        var sc = testData.materiaYComision();
        Classroom destino = testData.aula(testData.edificio());
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 2);

        impact(byRange(eventId, LocalDate.now().minusDays(1), start.plusWeeks(1), destino.getId()))
                .andExpect(status().isBadRequest());
    }
}
