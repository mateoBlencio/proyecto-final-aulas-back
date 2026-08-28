package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawAcademicEventMock;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "materias-mock-enabled", havingValue = "true")
public class AcademicEventMockViewFetcher {

    private final Resource fixture;
    private final ObjectMapper objectMapper;

    public AcademicEventMockViewFetcher(@Value("classpath:sysacad/mock/materia_comision.json") Resource fixture,
            ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.objectMapper = objectMapper;
    }

    public List<RawAcademicEventMock> fetch() {
        try (InputStream in = fixture.getInputStream()) {
            List<MockRow> rows = List.of(objectMapper.readValue(in, MockRow[].class));
            List<RawAcademicEventMock> flattened = rows.stream()
                    .flatMap(AcademicEventMockViewFetcher::toOccurrences)
                    .toList();
            log.warn("Usando MOCK de horario semanal por materia_comision ({} filas -> {} ocurrencias) — "
                    + "SysAcad no expone este dato todavía", rows.size(), flattened.size());
            return flattened;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el fixture mock de materia_comision", e);
        }
    }

    private static Stream<RawAcademicEventMock> toOccurrences(MockRow row) {
        if (row.horarios() == null) {
            return Stream.empty();
        }
        return row.horarios().stream()
                .map(schedule -> new RawAcademicEventMock(
                        row.curso(),
                        row.materia(),
                        schedule.dia(),
                        schedule.horaComienzo(),
                        schedule.duracion(),
                        schedule.horarioCuatrimestre()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MockRow(
            @JsonProperty("Curso") String curso,
            @JsonProperty("materia") Integer materia,
            @JsonProperty("Horarios") List<MockSchedule> horarios
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MockSchedule(
            @JsonProperty("Dia") Integer dia,
            @JsonProperty("HoraComienzo") String horaComienzo,
            @JsonProperty("DURACION") Integer duracion,
            @JsonProperty("HorarioCuatrimestre") Integer horarioCuatrimestre
    ) {}
}
