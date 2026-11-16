package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.AbstractDeactivationIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Reasignación directa de clases liberadas por baja de aula (integración)")
class DeactivationReallocationApiIntegrationTest extends AbstractDeactivationIntegrationTest {

    @Autowired
    private AllocationService allocationService;

    private Long releasedByDeactivation(LocalDate date) throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        Long occurrenceId = allocatedOccurrence(classroom, date);
        deactivateClassroom(classroom).andExpect(status().isOk());
        assertThat(occurrence(occurrenceId).getReleaseReason()).isEqualTo(ReleaseReason.CLASSROOM_DEACTIVATED);
        return occurrenceId;
    }

    private Long releasedByVirtualClass(LocalDate date) throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        Long occurrenceId = allocatedOccurrence(classroom, date);
        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + occurrenceId
                                + "]}],\"reason\":\"VIRTUAL_CLASS\"}"))
                .andExpect(status().isOk());
        return occurrenceId;
    }

    @Test
    @DisplayName("POST /v1/allocations sobre una principal liberada por baja: 201, vuelve a NEEDS_ROOM sin motivo ni nota y con asignacion")
    void asignarUnaLiberadaPorBaja() throws Exception {
        Long occurrenceId = releasedByDeactivation(futureMonday());
        Classroom target = testData.aula(testData.edificio());

        allocate(occurrenceId, target)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1));

        Occurrence reactivated = occurrence(occurrenceId);
        assertThat(reactivated.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(reactivated.getReleaseReason()).isNull();
        assertThat(reactivated.getReleaseNote()).isNull();
        assertAllocatedIn(occurrenceId, target);
    }

    @Test
    @DisplayName("lote mixto: la liberada por baja se asigna y la liberada como VIRTUAL_CLASS sigue liberada y no aparece en la respuesta")
    void loteMixtoSoloAsignaLaDeBaja() throws Exception {
        Long byDeactivation = releasedByDeactivation(futureMonday());
        Long byVirtualClass = releasedByVirtualClass(futureMonday().plusWeeks(1));
        Classroom target = testData.aula(testData.edificio());

        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + byDeactivation + "," + byVirtualClass
                                + "],\"classroomId\":" + target.getId() + "}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].occurrence.id").value(byDeactivation.intValue()));

        assertAllocatedIn(byDeactivation, target);
        Occurrence stillReleased = occurrence(byVirtualClass);
        assertThat(stillReleased.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(stillReleased.getReleaseReason()).isEqualTo(ReleaseReason.VIRTUAL_CLASS);
        assertThat(allocationOf(byVirtualClass)).isEmpty();
    }

    @Test
    @DisplayName("POST /v1/allocations con un espejo retirado por la baja (MIRROR_RETIRED): no lo asigna")
    void noAsignaElEspejoRetirado() throws Exception {
        Building building = testData.edificio();
        Classroom r204 = testData.aula(building);
        Classroom r205 = testData.aula(building);
        Long principal = allocatedOccurrence(r204, futureMonday());
        Long mirror = mirrorAllocatedIn(principal, r205);
        deactivateClassroom(r205).andExpect(status().isOk());
        Classroom target = testData.aula(testData.edificio());

        allocate(mirror, target)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(0));

        Occurrence retired = occurrence(mirror);
        assertThat(retired.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(retired.getReleaseReason()).isEqualTo(ReleaseReason.MIRROR_RETIRED);
        assertThat(allocationOf(mirror)).isEmpty();
    }

    @Test
    @DisplayName("PUT /v1/allocations por eventId: mueve la fecha con aula, no asigna la liberada por baja y no da 409")
    void reasignarElEventoNoAsignaLaLiberadaPorBaja() throws Exception {
        LocalDate first = futureMonday();
        var sc = testData.materiaYComision();
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, first, first.plusWeeks(1), 30);
        List<Occurrence> dates = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        Classroom stays = testData.aula(testData.edificio());
        Classroom dropped = testData.aula(testData.edificio());
        allocate(dates.get(0).getId(), stays).andExpect(status().isCreated());
        allocate(dates.get(1).getId(), dropped).andExpect(status().isCreated());
        deactivateClassroom(dropped).andExpect(status().isOk());
        Classroom target = testData.aula(testData.edificio());

        mockMvc.perform(put("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"eventId\":" + eventId + ",\"classroomId\":" + target.getId()
                                + "}],\"observation\":\"cambio de aula\"}"))
                .andExpect(status().isOk());

        assertAllocatedIn(dates.get(0).getId(), target);
        assertReleased(dates.get(1).getId(), ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, dropped);
    }

    @Test
    @DisplayName("POST /v1/allocations/releases con reason=CLASSROOM_DEACTIVATED responde 400 y no cambia nada")
    void noSePuedeLiberarConElMotivoInterno() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        Long occurrenceId = allocatedOccurrence(classroom, futureMonday());

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + occurrenceId
                                + "]}],\"reason\":\"CLASSROOM_DEACTIVATED\",\"note\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reasonSelectable").exists());

        assertAllocatedIn(occurrenceId, classroom);
    }

    @Test
    @DisplayName("syncFromSysacad no asigna una clase liberada por baja")
    void sysacadNoAsignaLaLiberadaPorBaja() throws Exception {
        Classroom classroom = testData.aula(testData.edificio());
        var sc = testData.materiaYComision();
        LocalDate date = futureMonday();
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 0), 90, date, date, 30);
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        allocate(occurrenceId, classroom).andExpect(status().isCreated());
        deactivateClassroom(classroom).andExpect(status().isOk());
        Classroom target = testData.aula(testData.edificio());

        int affected = SystemScope.call(() -> allocationService.syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(eventId), target.getId())), Set.of(eventId)));

        assertThat(affected).isZero();
        assertReleased(occurrenceId, ReleaseReason.CLASSROOM_DEACTIVATED, CLASSROOM_NOTE, classroom);
        assertThat(jdbc.queryForList("SELECT 1 FROM asignacion_aula WHERE origen = ? AND id_aula = ?",
                AllocationSource.SYSACAD.name(), target.getId())).isEmpty();
    }
}
