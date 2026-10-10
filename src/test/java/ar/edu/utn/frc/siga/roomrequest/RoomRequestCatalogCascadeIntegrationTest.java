package ar.edu.utn.frc.siga.roomrequest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(IntegrationTestData.class)
@DisplayName("Catálogos públicos de solicitudes: no listan elementos dados de baja ni los de padres dados de baja (integración)")
class RoomRequestCatalogCascadeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private StudyPlanRepository studyPlanRepository;
    @Autowired
    private BuildingRepository buildingRepository;

    private MockMvc anonymousMockMvc;

    @BeforeEach
    void setUpAnonymousMockMvc() {
        anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static int idOf(Long id) {
        return id.intValue();
    }

    @Test
    @DisplayName("comisiones de una materia: una comisión con período inhabilitado deja de aparecer")
    void commissionsCatalog_hidesCommissionOfInactivePeriod() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commissions")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(idOf(academic.commissionId()))));

        Commission commission = commissionRepository.findById(academic.commissionId()).orElseThrow();
        AcademicPeriod period = academicPeriodRepository.findById(commission.getAcademicPeriod().getId()).orElseThrow();
        period.deactivate();
        period = academicPeriodRepository.save(period);

        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commissions")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(idOf(academic.commissionId())))));

        academicPeriodRepository.restore(period);
    }

    @Test
    @DisplayName("comisiones de una materia: una comisión inhabilitada deja de aparecer aunque su vínculo siga activo")
    void commissionsCatalog_hidesInactiveCommission() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Commission commission = commissionRepository.findById(academic.commissionId()).orElseThrow();
        commission.deactivate();
        commissionRepository.save(commission);

        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commissions")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(idOf(academic.commissionId())))));
    }

    @Test
    @DisplayName("horario de una comisión inhabilitada: 404")
    void commissionSchedule_ofInactiveCommission_returnsNotFound() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Commission commission = commissionRepository.findById(academic.commissionId()).orElseThrow();
        commission.deactivate();
        commissionRepository.save(commission);

        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commission-schedule")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("commissionId", String.valueOf(academic.commissionId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("materias de una especialidad: una materia de un plan inhabilitado deja de aparecer")
    void subjectsCatalog_hidesSubjectOfInactivePlan() throws Exception {
        int specialtyCode = (int) IntegrationTestData.nextSeq();
        Specialty specialty = testData.especialidad(specialtyCode);
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), specialty);
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia catálogo", plan, "Anual");
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/subjects")
                        .param("specialtyCode", String.valueOf(specialtyCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(idOf(subject.getId()))));

        plan.deactivate();
        studyPlanRepository.save(plan);

        anonymousMockMvc.perform(get("/v1/room-requests/catalog/subjects")
                        .param("specialtyCode", String.valueOf(specialtyCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(idOf(subject.getId())))));
    }

    @Test
    @DisplayName("aulas: un aula activa de un edificio inhabilitado deja de ofrecerse")
    void classroomsCatalog_hidesClassroomOfInactiveBuilding() throws Exception {
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(idOf(classroom.getId()))));

        building.deactivate();
        buildingRepository.save(building);

        anonymousMockMvc.perform(get("/v1/room-requests/catalog/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(idOf(classroom.getId())))));
    }
}
