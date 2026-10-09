package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("POST /v1/audit/archive (integración)")
class AuditArchiveControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ARCHIVE = "/v1/audit/archive";

    @MockitoBean
    private AuditArchiveService archiveService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    @DisplayName("un usuario con solo PERM_AUDIT_READ recibe 403 y no se ejecuta el archivado")
    void auditReadAloneIsForbidden() throws Exception {
        MockMvc auditorOnly = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();

        auditorOnly.perform(post(ARCHIVE).with(user("auditor@frc.utn.edu.ar")
                        .authorities(new SimpleGrantedAuthority("PERM_AUDIT_READ"))))
                .andExpect(status().isForbidden());

        verify(archiveService, never()).archive(any());
    }

    @Test
    @DisplayName("un usuario con PERM_AUDIT_ARCHIVE recibe 200 (el permiso es el que habilita, no el rol)")
    void archivePermissionAloneIsAllowed() throws Exception {
        when(archiveService.archive(any())).thenReturn(
                new AuditArchiveResultDto(AuditArchiveOutcome.NO_PERIODS, null, null, 0, 0, 0));
        MockMvc archiver = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();

        archiver.perform(post(ARCHIVE).with(user("archivador@frc.utn.edu.ar")
                        .authorities(new SimpleGrantedAuthority("PERM_AUDIT_ARCHIVE"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un AUXILIAR_AULICO recibe 403")
    void auxiliaryIsForbidden() throws Exception {
        MockMvc auxMockMvc = mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);

        auxMockMvc.perform(post(ARCHIVE)).andExpect(status().isForbidden());

        verify(archiveService, never()).archive(any());
    }

    @Test
    @DisplayName("sin autenticar responde 401")
    void anonymousIsUnauthorized() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();

        anonymous.perform(post(ARCHIVE)).andExpect(status().isUnauthorized());

        verify(archiveService, never()).archive(any());
    }

    @Test
    @DisplayName("SUBSECRETARIA recibe 200 con el DTO del servicio y el servicio se llama con la fecha de hoy")
    void subsecretariaGetsTheServiceResult() throws Exception {
        when(archiveService.archive(any())).thenReturn(new AuditArchiveResultDto(
                AuditArchiveOutcome.COMPLETED, 2025, LocalDate.of(2025, 3, 3), 10, 3, 4));
        LocalDate before = LocalDate.now();

        mockMvc.perform(post(ARCHIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPLETED"))
                .andExpect(jsonPath("$.retainedFromCycle").value(2025))
                .andExpect(jsonPath("$.cutoff").value("2025-03-03"))
                .andExpect(jsonPath("$.occurrenceRows").value(10))
                .andExpect(jsonPath("$.allocationRows").value(3))
                .andExpect(jsonPath("$.deletedRevisions").value(4));

        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        verify(archiveService).archive(today.capture());
        assertThat(today.getValue()).isBetween(before, LocalDate.now());
    }

    @Test
    @DisplayName("NO_PERIODS responde 200 con retainedFromCycle y cutoff nulos")
    void noPeriodsIsStillOk() throws Exception {
        when(archiveService.archive(any())).thenReturn(
                new AuditArchiveResultDto(AuditArchiveOutcome.NO_PERIODS, null, null, 0, 0, 0));

        mockMvc.perform(post(ARCHIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_PERIODS"))
                .andExpect(jsonPath("$.retainedFromCycle").doesNotExist())
                .andExpect(jsonPath("$.cutoff").doesNotExist());
    }
}
