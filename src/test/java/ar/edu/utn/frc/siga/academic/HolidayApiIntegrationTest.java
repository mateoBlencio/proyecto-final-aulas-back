package ar.edu.utn.frc.siga.academic;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.Holiday;
import ar.edu.utn.frc.siga.academic.repository.HolidayRepository;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Holiday - GET /v1/holidays (integración)")
class HolidayApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private HolidayService holidayService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanHolidays() {
        holidayRepository.deleteAll();
    }

    private void save(LocalDate date, String description) {
        holidayRepository.save(Holiday.builder().date(date).description(description)
                .syncedAt(Instant.parse("2026-10-10T12:00:00Z")).build());
    }

    @Test
    @DisplayName("returns only the holidays inside the range, ordered by date")
    void returnsRangeOrderedByDate() throws Exception {
        save(LocalDate.of(2026, 7, 20), "Late July");
        save(LocalDate.of(2026, 6, 30), "Before range");
        save(LocalDate.of(2026, 7, 9), "Independence Day");
        save(LocalDate.of(2026, 8, 1), "After range");
        save(LocalDate.of(2026, 7, 1), "Range start");
        save(LocalDate.of(2026, 7, 31), "Range end");

        mockMvc.perform(get("/v1/holidays").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$[1].date").value("2026-07-09"))
                .andExpect(jsonPath("$[1].description").value("Independence Day"))
                .andExpect(jsonPath("$[2].date").value("2026-07-20"))
                .andExpect(jsonPath("$[3].date").value("2026-07-31"));
    }

    @Test
    @DisplayName("does not return soft-deleted holidays")
    void excludesSoftDeletedHolidays() throws Exception {
        save(LocalDate.of(2026, 7, 9), "Independence Day");
        holidayRepository.save(Holiday.builder().date(LocalDate.of(2026, 7, 20)).description("Dado de baja")
                .syncedAt(Instant.parse("2026-10-10T12:00:00Z"))
                .deletedAt(Instant.parse("2026-10-11T12:00:00Z")).build());

        mockMvc.perform(get("/v1/holidays").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-07-09"));
    }

    @Test
    @DisplayName("eliminado_en exists and is nullable in feriado and feriado_aud")
    void deletedAtColumnExistsAndIsNullableInBothTables() {
        for (String table : List.of("feriado", "feriado_aud")) {
            List<String> nullable = jdbc.queryForList(
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'eliminado_en'",
                    String.class, table);
            assertThat(nullable).as("columna eliminado_en en " + table).containsExactly("YES");
        }
    }

    @Test
    @DisplayName("sync soft-deletes a future holiday that stops coming, audits it, and reactivates it when it comes back")
    void syncSoftDeletesAndReactivatesFutureHoliday() throws Exception {
        LocalDate gone = LocalDate.of(2095, 3, 5);
        LocalDate stays = LocalDate.of(2095, 3, 6);

        holidayService.syncHolidays(List.of(
                new HolidaySyncCommand(gone, "Feriado A"), new HolidaySyncCommand(stays, "Feriado B")));
        holidayService.syncHolidays(List.of(new HolidaySyncCommand(stays, "Feriado B")));

        mockMvc.perform(get("/v1/holidays").param("from", "2095-03-01").param("to", "2095-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2095-03-06"));
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NOT NULL FROM feriado WHERE fecha = ?",
                Boolean.class, gone)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM feriado_aud WHERE fecha = ? AND revtype = 1 AND eliminado_en IS NOT NULL",
                Long.class, gone)).isEqualTo(1L);

        holidayService.syncHolidays(List.of(
                new HolidaySyncCommand(gone, "Feriado A reactivado"), new HolidaySyncCommand(stays, "Feriado B")));

        mockMvc.perform(get("/v1/holidays").param("from", "2095-03-01").param("to", "2095-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2095-03-05"))
                .andExpect(jsonPath("$[0].description").value("Feriado A reactivado"));
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NULL FROM feriado WHERE fecha = ?",
                Boolean.class, gone)).isTrue();
    }

    @Test
    @DisplayName("to before from returns 400")
    void toBeforeFromIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/holidays").param("from", "2026-07-31").param("to", "2026-07-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("missing to returns 400")
    void missingToIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/holidays").param("from", "2026-07-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a CONSULTA user can read holidays")
    void consultaUserCanRead() throws Exception {
        MockMvc consulta = mockMvcAs("consulta-holidays@frc.utn.edu.ar", SystemRole.CONSULTA);

        consulta.perform(get("/v1/holidays").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an authenticated user without roles gets 403")
    void userWithoutRolesIsForbidden() throws Exception {
        String email = "no-roles-holidays@frc.utn.edu.ar";
        userRepository.findByEmailAndEnabledTrue(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("integration-test-fixture-password"))
                .enabled(true)
                .firstName("No")
                .lastName("Roles")
                .build()));
        MockMvc noRoles = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + jwtService.generateAccessToken(email)))
                .build();

        noRoles.perform(get("/v1/holidays").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isForbidden());
    }
}
