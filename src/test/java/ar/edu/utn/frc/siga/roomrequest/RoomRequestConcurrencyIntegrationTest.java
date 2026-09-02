package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateConferenceDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.RequesterInfo;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El {@code orden} de cada pedido se calcula en memoria ({@code items.size() + 1}) y lo respalda el
 * unique {@code uq_solicitud_item_orden}. Este test fija que ese cálculo aguanta altas simultáneas:
 * como el orden es único <b>por solicitud</b> y cada alta crea su propia cabecera, dos altas en
 * paralelo nunca compiten por la misma clave.
 */
@Import(IntegrationTestData.class)
@DisplayName("Solicitudes de aula concurrentes (integración)")
class RoomRequestConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private RoomRequestService roomRequestService;

    @Test
    @DisplayName("ocho altas simultáneas de dos pedidos: ninguna viola el unique de orden")
    void concurrentCreates_doNotCollideOnItemPosition() throws Exception {
        List<Callable<RoomRequestResponseDto>> altas = IntStream.range(0, THREADS)
                .<Callable<RoomRequestResponseDto>>mapToObj(i -> () -> roomRequestService.create(twoItemRequest(i)))
                .toList();

        List<Future<RoomRequestResponseDto>> futures;
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            futures = pool.invokeAll(altas, 30, TimeUnit.SECONDS);
        }

        List<RoomRequestResponseDto> creadas = new java.util.ArrayList<>();
        for (Future<RoomRequestResponseDto> future : futures) {
            creadas.add(future.get());
        }

        assertThat(creadas).hasSize(THREADS);
        assertThat(creadas).extracting(RoomRequestResponseDto::id).doesNotHaveDuplicates();
        assertThat(creadas).allSatisfy(solicitud ->
                assertThat(solicitud.items()).extracting(item -> item.position()).containsExactly(1, 2));
    }

    private CreateRoomRequestDto twoItemRequest(int index) {
        RequesterInfo requester = new RequesterInfo(AcademicScope.EXTENSION, "Docente " + index,
                "docente" + index + "@frc.utn.edu.ar", "351-000000" + index);
        return new CreateConferenceDto(RoomRequestType.CONFERENCE, requester, null,
                List.of(item(LocalDate.now().plusDays(10)), item(LocalDate.now().plusDays(11))));
    }

    private FreeFormItemDto item(LocalDate date) {
        return new FreeFormItemDto(null, date, LocalTime.of(10, 0), LocalTime.of(12, 0),
                35, 1, false, false, null, null, null, null, List.of());
    }
}
