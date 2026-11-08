package ar.edu.utn.frc.siga.sysacad;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/sysacad/write/preview (integración, flag encendido)")
@TestPropertySource(properties = "siga.sysacad.enabled=true")
class SysacadWritePreviewIntegrationTest extends AbstractIntegrationTest {

    private static final int YEAR = 2096;

    @MockitoBean
    private SysacadClient sysacadClient;
    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanTestRows() {
        jdbc.update("DELETE FROM sysacad_horario WHERE anio = ?", YEAR);
    }

    private record Seeded(long scheduleId, long occurrenceId, int buildingCode, int targetRoom) {
    }

    private Seeded seedScheduleWithClassInOtherRoom() {
        int buildingCode = 10_000 + (int) (IntegrationTestData.nextSeq() % 1_000_000);
        Building building = testData.edificio();
        building.setBuildingCode(buildingCode);
        building = buildingRepository.save(building);
        Classroom known = testData.aulaConNumero(228, building, testData.tipoAulaPorDefecto(), 40);
        Classroom target = testData.aulaConNumero(229, building, testData.tipoAulaPorDefecto(), 40);

        SubjectAndCommission academic = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 500);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), date.getDayOfWeek(),
                LocalTime.of(8, 0), 90, date, date, 30);
        jdbc.update("UPDATE evento_academico SET hash_raw = 'h-' || id_evento_academico, habilitado_sysacad = TRUE "
                + "WHERE id_evento_academico = ?", eventId);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(target.getId()).source(AllocationSource.MANUAL).build());

        Long scheduleId = jdbc.queryForObject("INSERT INTO sysacad_horario (anio, especialidad, plan, materia, "
                + "comision, dia, cuatrimestre, curso, edificio_conocido, aula_conocida, id_evento_1) "
                + "VALUES (?, 1, 2008, ?, 7, 1, 1, '1R1', ?, 228, ?) RETURNING id_horario", Long.class,
                YEAR, (int) IntegrationTestData.nextSeq(), buildingCode, eventId);
        return new Seeded(scheduleId, occurrence.getId(), buildingCode, target.getRoomNumber());
    }

    @Test
    @DisplayName("Subsecretaría recibe 200 con la decisión SEND de la próxima clase y no se llama al cliente de SysAcad")
    void preview_asSubsecretaria_returnsRealDecisionWithoutCallingSysacad() throws Exception {
        Seeded seeded = seedScheduleWithClassInOtherRoom();

        mockMvc.perform(get("/v1/sysacad/write/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.scheduleId==" + seeded.scheduleId() + ")].decision").value("SEND"))
                .andExpect(jsonPath("$[?(@.scheduleId==" + seeded.scheduleId() + ")].targetRoom")
                        .value(seeded.targetRoom()))
                .andExpect(jsonPath("$[?(@.scheduleId==" + seeded.scheduleId() + ")].targetBuilding")
                        .value(seeded.buildingCode()))
                .andExpect(jsonPath("$[?(@.scheduleId==" + seeded.scheduleId() + ")].knownRoom").value(228))
                .andExpect(jsonPath("$[?(@.scheduleId==" + seeded.scheduleId() + ")].occurrenceId")
                        .value((int) seeded.occurrenceId()));

        verifyNoInteractions(sysacadClient);
    }

    @Test
    @DisplayName("Un horario cuya próxima clase ya está en el aula conocida no aparece en la vista previa")
    void preview_omitsUnchangedSchedules() throws Exception {
        Seeded seeded = seedScheduleWithClassInOtherRoom();
        jdbc.update("UPDATE sysacad_horario SET aula_conocida = ? WHERE id_horario = ?", seeded.targetRoom(),
                seeded.scheduleId());

        mockMvc.perform(get("/v1/sysacad/write/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.scheduleId==" + seeded.scheduleId() + ")]").isEmpty());
    }

    @Test
    @DisplayName("Un AUXILIAR_AULICO sin PERM_SYSACAD_READ recibe 403")
    void preview_asAuxiliar_isForbidden() throws Exception {
        mockMvcAsAuxiliar("auxiliar@frc.utn.edu.ar").perform(get("/v1/sysacad/write/preview"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sysacadClient);
    }
}
