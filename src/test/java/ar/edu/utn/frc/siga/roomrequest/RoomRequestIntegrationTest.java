package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
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
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom preferred = testData.aula(building);
        Classroom current = testData.aula(building);

        CreateRoomRequestDto dto = new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                academic.subjectId(),
                List.of(
                        item(academic.commissionId(), LocalDate.now().plusDays(10), current.getId(),
                                List.of(preferred.getId())),
                        item(academic.commissionId(), LocalDate.now().plusDays(11), null, List.of())));

        RoomRequestResponseDto created = roomRequestService.create(dto);

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.items()).allSatisfy(
                item -> assertThat(item.status()).isEqualTo(RoomRequestStatus.PENDING));
        assertThat(created.subject().id()).isEqualTo(academic.subjectId());
        assertThat(created.items()).hasSize(2);

        var first = created.items().getFirst();
        assertThat(first.position()).isEqualTo(1);
        assertThat(first.durationMinutes()).isEqualTo(120);
        assertThat(first.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(first.commission().id()).isEqualTo(academic.commissionId());
        assertThat(first.currentClassroom().id()).isEqualTo(current.getId());
        assertThat(first.preferredClassrooms()).singleElement()
                .satisfies(room -> assertThat(room.id()).isEqualTo(preferred.getId()));
        assertThat(created.items().get(1).position()).isEqualTo(2);
        assertThat(created.items().get(1).preferredClassrooms()).isEmpty();
    }

    @Test
    @DisplayName("preferencias múltiples: se guardan en el orden de prioridad declarado")
    void create_withSeveralPreferences_keepsDeclaredOrder() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom first = testData.aula(building);
        Classroom second = testData.aula(building);
        Classroom third = testData.aula(building);
        List<Long> priority = List.of(third.getId(), first.getId(), second.getId());

        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                academic.subjectId(),
                List.of(item(academic.commissionId(), LocalDate.now().plusDays(10), null, priority))));

        assertThat(created.items().getFirst().preferredClassrooms())
                .extracting(ClassroomOptionDto::id)
                .containsExactlyElementsOf(priority);
    }

    @Test
    @DisplayName("booleanos opcionales en null: projector/computers se persisten como false, examUsers queda null")
    void create_withNullBooleans_normalizesToFalse() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        CreateRoomRequestItemDto itemWithNulls = new CreateRoomRequestItemDto(
                academic.commissionId(), LocalDate.now().plusDays(5),
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                30, 35, 1, null,
                null, null, null, null, null, null, null);

        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                academic.subjectId(),
                List.of(itemWithNulls)));

        var item = created.items().getFirst();
        assertThat(item.requiresProjector()).isFalse();
        assertThat(item.requiresComputers()).isFalse();
        assertThat(item.requiresExamUsers()).isNull();
        assertThat(item.preferredClassrooms()).isEmpty();
    }

    @Test
    @DisplayName("examUsers en un parcial con computadoras: se acepta y se persiste")
    void create_examWithComputers_acceptsExamUsers() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        CreateRoomRequestItemDto itemWithExamUsers = new CreateRoomRequestItemDto(
                academic.commissionId(), LocalDate.now().plusDays(5),
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                30, 35, 1, null,
                true, true, 20, true, "Office", null, List.of());

        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                academic.subjectId(),
                List.of(itemWithExamUsers)));

        var item = created.items().getFirst();
        assertThat(item.requiresComputers()).isTrue();
        assertThat(item.requiresExamUsers()).isTrue();
    }

    @Test
    @DisplayName("examUsers en una conferencia: se rechaza porque no es parcial ni final")
    void create_examUsers_onNonExamType_isRejected() {
        CreateRoomRequestItemDto itemWithExamUsers = new CreateRoomRequestItemDto(
                null, LocalDate.now().plusDays(5),
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                30, 35, 1, null,
                true, true, 20, true, "Office", null, List.of());

        CreateRoomRequestDto dto = new CreateRoomRequestDto(
                RoomRequestType.CONFERENCE,
                AcademicScope.EXTENSION,
                "Grace Hopper",
                "grace@frc.utn.edu.ar",
                "351-0000000",
                null,
                List.of(itemWithExamUsers));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("requiresExamUsers");
    }

    @Test
    @DisplayName("conferencia sin materia ni comisión: se acepta")
    void create_conference_withoutAcademicReference_isAccepted() {
        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.CONFERENCE,
                AcademicScope.EXTENSION,
                "Grace Hopper",
                "grace@frc.utn.edu.ar",
                "351-0000000",
                null,
                List.of(item(null, LocalDate.now().plusDays(20), null, List.of()))));

        assertThat(created.subject()).isNull();
        assertThat(created.items()).singleElement()
                .satisfies(item -> assertThat(item.commission()).isNull());
    }

    @Test
    @DisplayName("parcial sin materia: se rechaza por referencia académica faltante")
    void create_partialExam_withoutSubject_isRejected() {
        CreateRoomRequestDto dto = new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                null,
                List.of(item(null, LocalDate.now().plusDays(3), null, List.of())));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("subjectId es obligatorio");
    }

    @Test
    @DisplayName("tipo OTHER con observations: se persiste y el enum vuelve intacto de la base")
    void create_otherType_roundTripsThroughDatabase() {
        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.OTHER,
                AcademicScope.EXTENSION,
                "Grace Hopper",
                "grace@frc.utn.edu.ar",
                "351-0000000",
                null,
                List.of(item(null, LocalDate.now().plusDays(15), null, List.of()))));

        assertThat(created.type()).isEqualTo(RoomRequestType.OTHER);
        assertThat(created.subject()).isNull();
        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(RoomRequestStatus.PENDING);
            assertThat(item.observations()).isEqualTo("Observación de prueba");
        });
    }

    @Test
    @DisplayName("tipo OTHER sin observations: se rechaza, es el único dato que describe el pedido")
    void create_otherType_withoutObservations_isRejected() {
        CreateRoomRequestItemDto itemWithoutObservations = new CreateRoomRequestItemDto(
                null, LocalDate.now().plusDays(15),
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                30, 35, 1, null,
                false, false, null, null, null, null, List.of());

        CreateRoomRequestDto dto = new CreateRoomRequestDto(
                RoomRequestType.OTHER,
                AcademicScope.EXTENSION,
                "Grace Hopper",
                "grace@frc.utn.edu.ar",
                "351-0000000",
                null,
                List.of(itemWithoutObservations));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("observations");
    }

    private CreateRoomRequestItemDto item(Long commissionId, LocalDate date,
                                          Long currentClassroomId, List<Long> preferredClassroomIds) {
        return new CreateRoomRequestItemDto(
                commissionId,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                30,
                35,
                1,
                currentClassroomId,
                true,
                false,
                null,
                null,
                null,
                "Observación de prueba",
                preferredClassroomIds);
    }
}
