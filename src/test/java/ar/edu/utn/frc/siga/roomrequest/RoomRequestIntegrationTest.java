package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateConferenceDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOtherDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamOffScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.RequesterInfo;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(IntegrationTestData.class)
@DisplayName("Solicitudes de aula (integración)")
class RoomRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private RoomRequestService roomRequestService;

    @Test
    @DisplayName("crear con dos pedidos y preferencias: persiste todo y lo devuelve compuesto")
    void create_withItemsAndPreferences_persistsAndComposes() {
        Building building = testData.edificio();
        Classroom preferred = testData.aula(building);

        RoomRequestResponseDto created = roomRequestService.create(new CreateConferenceDto(
                RoomRequestType.CONFERENCE, requester(), null,
                List.of(item(null, LocalDate.now().plusDays(10), List.of(preferred.getId())),
                        item(null, LocalDate.now().plusDays(11), List.of()))));

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.items()).allSatisfy(
                item -> assertThat(item.status()).isEqualTo(RoomRequestStatus.PENDING));
        assertThat(created.items()).hasSize(2);

        var first = created.items().getFirst();
        assertThat(first.position()).isEqualTo(1);
        assertThat(first.durationMinutes()).isEqualTo(120);
        assertThat(first.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(first.preferredClassrooms()).singleElement()
                .satisfies(room -> assertThat(room.id()).isEqualTo(preferred.getId()));
        assertThat(created.items().get(1).position()).isEqualTo(2);
        assertThat(created.items().get(1).preferredClassrooms()).isEmpty();
    }

    @Test
    @DisplayName("preferencias múltiples: se guardan en el orden de prioridad declarado")
    void create_withSeveralPreferences_keepsDeclaredOrder() {
        Building building = testData.edificio();
        Classroom first = testData.aula(building);
        Classroom second = testData.aula(building);
        Classroom third = testData.aula(building);
        List<Long> priority = List.of(third.getId(), first.getId(), second.getId());

        RoomRequestResponseDto created = roomRequestService.create(new CreateConferenceDto(
                RoomRequestType.CONFERENCE, requester(), null,
                List.of(item(null, LocalDate.now().plusDays(10), priority))));

        assertThat(created.items().getFirst().preferredClassrooms())
                .extracting(ClassroomOptionDto::id)
                .containsExactlyElementsOf(priority);
    }

    @Test
    @DisplayName("booleanos opcionales en null: projector/computers se persisten como false, examUsers queda null")
    void create_withNullBooleans_normalizesToFalse() {
        FreeFormItemDto itemWithNulls = new FreeFormItemDto(null, LocalDate.now().plusDays(5),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 35, null,
                null, null, null, null, null, null, null);

        RoomRequestResponseDto created = roomRequestService.create(new CreateConferenceDto(
                RoomRequestType.CONFERENCE, requester(), null, List.of(itemWithNulls)));

        var item = created.items().getFirst();
        assertThat(item.requiresProjector()).isFalse();
        assertThat(item.requiresComputers()).isFalse();
        assertThat(item.requiresExamUsers()).isNull();
        assertThat(item.classroomCount()).isEqualTo(1);
        assertThat(item.preferredClassrooms()).isEmpty();
    }

    @Test
    @DisplayName("examUsers en un parcial con computadoras: se acepta y se persiste")
    void create_examWithComputers_acceptsExamUsers() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        FreeFormItemDto itemWithExamUsers = new FreeFormItemDto(academic.commissionId(),
                LocalDate.now().plusDays(5), LocalTime.of(10, 0), LocalTime.of(12, 0), 35, 1,
                true, true, 20, true, "Office", null, List.of());

        RoomRequestResponseDto created = roomRequestService.create(new CreatePartialExamOffScheduleDto(
                RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, requester(), academic.subjectId(),
                List.of(itemWithExamUsers)));

        var item = created.items().getFirst();
        assertThat(item.requiresComputers()).isTrue();
        assertThat(item.requiresExamUsers()).isTrue();
    }

    @Test
    @DisplayName("examUsers en una conferencia: se rechaza porque no es un examen")
    void create_examUsers_onNonExamType_isRejected() {
        FreeFormItemDto itemWithExamUsers = new FreeFormItemDto(null, LocalDate.now().plusDays(5),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 35, 1,
                true, true, 20, true, "Office", null, List.of());

        CreateConferenceDto dto = new CreateConferenceDto(RoomRequestType.CONFERENCE,
                new RequesterInfo(AcademicScope.EXTENSION, "Grace Hopper", "grace@frc.utn.edu.ar", "351-0000000"),
                null, List.of(itemWithExamUsers));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("requiresExamUsers");
    }

    @Test
    @DisplayName("conferencia sin materia: se acepta y el pedido queda sin comisión")
    void create_conference_withoutAcademicReference_isAccepted() {
        RoomRequestResponseDto created = roomRequestService.create(new CreateConferenceDto(
                RoomRequestType.CONFERENCE, requester(), null,
                List.of(item(null, LocalDate.now().plusDays(20), List.of()))));

        assertThat(created.subject()).isNull();
        assertThat(created.items()).singleElement()
                .satisfies(item -> assertThat(item.commission()).isNull());
    }

    @Test
    @DisplayName("conferencia con comisión en el pedido: se rechaza, no pertenece al cursado de ninguna")
    void create_conference_withCommission_isRejected() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        CreateConferenceDto dto = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                academic.subjectId(),
                List.of(item(academic.commissionId(), LocalDate.now().plusDays(20), List.of())));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no llevan comisión");
    }

    @Test
    @DisplayName("tipo OTHER con comisión en el pedido: se rechaza igual que la conferencia")
    void create_otherType_withCommission_isRejected() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        FreeFormItemDto withCommission = new FreeFormItemDto(academic.commissionId(),
                LocalDate.now().plusDays(15), LocalTime.of(10, 0), LocalTime.of(12, 0),
                35, 1, false, false, null, null, null, "Un evento cualquiera", List.of());
        CreateOtherDto dto = new CreateOtherDto(RoomRequestType.OTHER, requester(),
                academic.subjectId(), List.of(withCommission));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no llevan comisión");
    }

    @Test
    @DisplayName("parcial fuera de horario sin materia: se rechaza por referencia académica faltante")
    void create_partialExam_withoutSubject_isRejected() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        CreatePartialExamOffScheduleDto dto = new CreatePartialExamOffScheduleDto(
                RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, requester(), null,
                List.of(item(academic.commissionId(), LocalDate.now().plusDays(3), List.of())));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("subjectId es obligatorio");
    }

    @Test
    @DisplayName("tipo OTHER con observations: se persiste y el enum vuelve intacto de la base")
    void create_otherType_roundTripsThroughDatabase() {
        RoomRequestResponseDto created = roomRequestService.create(new CreateOtherDto(
                RoomRequestType.OTHER, requester(), null,
                List.of(otherItem("Necesito el aula para un evento no contemplado"))));

        assertThat(created.type()).isEqualTo(RoomRequestType.OTHER);
        assertThat(created.subject()).isNull();
        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(RoomRequestStatus.PENDING);
            assertThat(item.observations()).isEqualTo("Necesito el aula para un evento no contemplado");
        });
    }

    @Test
    @DisplayName("tipo OTHER sin observations: se rechaza, es el único dato que describe el pedido")
    void create_otherType_withoutObservations_isRejected() {
        CreateOtherDto dto = new CreateOtherDto(RoomRequestType.OTHER, requester(), null,
                List.of(item(null, LocalDate.now().plusDays(15), List.of())));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("observations");
    }

    private static RequesterInfo requester() {
        return new RequesterInfo(AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567");
    }

    private FreeFormItemDto item(Long commissionId, LocalDate date, List<Long> preferredClassroomIds) {
        return new FreeFormItemDto(commissionId, date, LocalTime.of(10, 0), LocalTime.of(12, 0),
                35, 1, true, false, null, null, null, null, preferredClassroomIds);
    }

    private FreeFormItemDto otherItem(String observations) {
        return new FreeFormItemDto(null, LocalDate.now().plusDays(15), LocalTime.of(10, 0), LocalTime.of(12, 0),
                35, 1, false, false, null, null, null, observations, List.of());
    }
}
