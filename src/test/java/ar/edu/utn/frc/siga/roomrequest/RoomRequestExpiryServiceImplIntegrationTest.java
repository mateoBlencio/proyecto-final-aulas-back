package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(IntegrationTestData.class)
@DisplayName("Vencimiento automático de pedidos de aula (integración)")
class RoomRequestExpiryServiceImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private RoomRequestExpiryService expiryService;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("cancela NEW/DERIVED_TO_BUILDING vencidos, respeta el corte estricto y no toca RESOLVED/CANCELLED")
    void expiresOverdueItemsOnly() {
        Long overdueNewId = seedItem(RoomRequestStatus.NEW, LocalDate.now().minusDays(1)).getId();
        Long overdueDerivedId = seedItem(RoomRequestStatus.DERIVED_TO_BUILDING, LocalDate.now().minusDays(1)).getId();
        Long notOverdueTodayId = seedItem(RoomRequestStatus.NEW, LocalDate.now()).getId();
        Long resolvedId = seedItem(RoomRequestStatus.RESOLVED, LocalDate.now().minusDays(1)).getId();
        Long cancelledId = seedItem(RoomRequestStatus.CANCELLED, LocalDate.now().minusDays(1)).getId();

        expiryService.expireOverdueItems();

        assertThat(itemRepository.findById(overdueNewId).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(itemRepository.findById(overdueNewId).orElseThrow().getDecidedBy()).isEqualTo("SISTEMA");
        assertThat(itemRepository.findById(overdueDerivedId).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(itemRepository.findById(notOverdueTodayId).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.NEW);
        assertThat(itemRepository.findById(resolvedId).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(itemRepository.findById(cancelledId).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("un IN_EVALUATION vencido con aula asignada queda CANCELLED con la asignación intacta")
    void expiredItemKeepsAllocationsIntact() {
        Long classroomId = testData.aula(testData.edificio()).getId();
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate pastOccurrenceDate = LocalDate.now().minusDays(1);
        var eventDto = new CreateRecurringEventRequestDto(30, LocalTime.of(9, 0), 90,
                pastOccurrenceDate.getDayOfWeek(), pastOccurrenceDate, pastOccurrenceDate,
                academic.subjectId(), academic.commissionId());
        Long eventId = academicEventService.createRecurringEvent(eventDto).id();
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();

        RoomRequest request = baseRequest(academic);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(pastOccurrenceDate)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(1)
                .status(RoomRequestStatus.IN_EVALUATION)
                .decidedBy("subsecretaria@frc.utn.edu.ar")
                .decidedAt(java.time.LocalDateTime.now())
                .build();
        item.assignClassrooms(java.util.List.of(classroomId), java.util.List.of(java.util.List.of(occurrenceId)));
        request.addItem(item);
        roomRequestRepository.save(request);

        expiryService.expireOverdueItems();

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.CANCELLED);
        Long allocationCount = jdbcTemplate.queryForObject(
                "select count(*) from solicitud_item_asignacion where id_item = ?", Long.class, item.getId());
        assertThat(allocationCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE vence con el endDate pasado del evento recurrente")
    void expiresRegularRoomChangeWithPastEndDate() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate start = LocalDate.now().minusMonths(2);
        LocalDate end = LocalDate.now().minusDays(1);
        var eventDto = new CreateRecurringEventRequestDto(30, LocalTime.of(9, 0), 90,
                start.getDayOfWeek(), start, end, academic.subjectId(), academic.commissionId());
        Long eventId = academicEventService.createRecurringEvent(eventDto).id();

        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.REGULAR_ROOM_CHANGE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .dayOfWeek(start.getDayOfWeek())
                .sourceRecurringEventId(eventId)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .classroomCount(1)
                .status(RoomRequestStatus.NEW)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);

        expiryService.expireOverdueItems();

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE con endDate nulo no vence")
    void doesNotExpireRegularRoomChangeWithoutEndDate() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate start = LocalDate.now().minusMonths(2);
        var eventDto = new CreateRecurringEventRequestDto(30, LocalTime.of(9, 0), 90,
                start.getDayOfWeek(), start, null, academic.subjectId(), academic.commissionId());
        Long eventId = academicEventService.createRecurringEvent(eventDto).id();

        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.REGULAR_ROOM_CHANGE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .dayOfWeek(start.getDayOfWeek())
                .sourceRecurringEventId(eventId)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .classroomCount(1)
                .status(RoomRequestStatus.NEW)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);

        expiryService.expireOverdueItems();

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.NEW);
    }

    @Test
    @DisplayName("el vencimiento no publica ningún evento de dominio")
    void doesNotPublishAnyEvent() {
        seedItem(RoomRequestStatus.NEW, LocalDate.now().minusDays(1));

        Long before = jdbcTemplate.queryForObject("select count(*) from event_publication", Long.class);
        expiryService.expireOverdueItems();
        Long after = jdbcTemplate.queryForObject("select count(*) from event_publication", Long.class);

        assertThat(after).isEqualTo(before);
    }

    private RoomRequestItem seedItem(RoomRequestStatus status, LocalDate date) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = baseRequest(academic);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(1)
                .build();
        request.addItem(item);
        if (status != RoomRequestStatus.NEW) {
            item.decide(status, "subsecretaria@frc.utn.edu.ar", "motivo de prueba", java.time.LocalDateTime.now());
        }
        roomRequestRepository.save(request);
        return item;
    }

    private RoomRequest baseRequest(IntegrationTestData.SubjectAndCommission academic) {
        return RoomRequest.builder()
                .type(RoomRequestType.ONE_TIME_ROOM_CHANGE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
    }
}
