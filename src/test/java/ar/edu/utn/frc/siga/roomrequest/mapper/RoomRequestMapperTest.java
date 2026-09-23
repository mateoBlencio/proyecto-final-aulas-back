package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.BuildingOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestMapper (aislado)")
class RoomRequestMapperTest {

    private final RoomRequestMapper mapper = new RoomRequestMapperImpl();

    @Test
    @DisplayName("la cabecera se arma con la materia y los items que le pasa el composer")
    void headerUsesComposedPieces() {
        SubjectResponseDto subject = new SubjectResponseDto(42L, 101, "Análisis Matemático I", "ANUAL", null);
        RoomRequestItemResponseDto itemDto =
                mapper.toDto(itemEntity(), null, List.of(), List.of(), List.of(), null, null, null, null);

        RoomRequestResponseDto dto = mapper.toDto(requestEntity(), subject, List.of(itemDto));

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.type()).isEqualTo(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE);
        assertThat(dto.teacherName()).isEqualTo("Ada Lovelace");
        assertThat(dto.subject()).isSameAs(subject);
        assertThat(dto.items()).containsExactly(itemDto);
    }

    @Test
    @DisplayName("sin materia: la cabecera queda con subject en null")
    void nullSubjectIsAllowed() {
        RoomRequestResponseDto dto = mapper.toDto(requestEntity(), null, List.of());

        assertThat(dto.subject()).isNull();
        assertThat(dto.items()).isEmpty();
    }

    @Test
    @DisplayName("el pedido deriva endTime y durationMinutes de la duración guardada")
    void itemDerivesEndTimeAndDuration() {
        RoomRequestItemResponseDto dto =
                mapper.toDto(itemEntity(), null, List.of(), List.of(), List.of(), null, null, null, null);

        assertThat(dto.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(dto.durationMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("día de dictado y estimado viajan al DTO")
    void itemCarriesDayOfWeekAndEstimated() {
        RoomRequestItem entity = RoomRequestItem.builder()
                .id(5L).position(1).commissionId(7L)
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(120))
                .estimated(35).classroomCount(1)
                .build();

        RoomRequestItemResponseDto dto =
                mapper.toDto(entity, null, List.of(), List.of(), List.of(), null, null, null, null);

        assertThat(dto.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(dto.date()).isNull();
        assertThat(dto.estimated()).isEqualTo(35);
    }

    @Test
    @DisplayName("enrolled y aula(s) actual(es) se pegan tal cual las resolvió el composer")
    void itemUsesEnrolledAndCurrentClassroomsPieces() {
        List<ClassroomOptionDto> current = List.of(new ClassroomOptionDto(9L, 101, "Edif. Ing. Maders"));

        RoomRequestItemResponseDto dto =
                mapper.toDto(itemEntity(), null, List.of(), current, List.of(), null, null, null, 45);

        assertThat(dto.enrolled()).isEqualTo(45);
        assertThat(dto.currentClassrooms()).isEqualTo(current);
    }

    @Test
    @DisplayName("un tipo sin aula actual resolviéndose no manda currentClassrooms ni enrolled")
    void itemWithoutSourceEventLeavesEnrolledAndCurrentClassroomsNull() {
        RoomRequestItemResponseDto dto =
                mapper.toDto(itemEntity(), null, List.of(), null, List.of(), null, null, null, null);

        assertThat(dto.enrolled()).isNull();
        assertThat(dto.currentClassrooms()).isNull();
    }

    @Test
    @DisplayName("comisión y aulas de preferencia se pegan tal cual las resolvió el composer")
    void itemUsesComposedPieces() {
        CommissionResponseDto commission = new CommissionResponseDto(7L, "3K1", null);
        List<ClassroomOptionDto> preferred = List.of(new ClassroomOptionDto(11L, 11, "Pabellón"));

        RoomRequestItemResponseDto dto =
                mapper.toDto(itemEntity(), List.of(commission), preferred, List.of(), List.of(), null, null, null, null);

        assertThat(dto.commissions()).containsExactly(commission);
        assertThat(dto.preferredClassrooms()).isEqualTo(preferred);
    }

    @Test
    @DisplayName("el estado del pedido viaja al DTO")
    void itemCarriesStatus() {
        assertThat(mapper.toDto(itemEntity(), null, List.of(), List.of(), List.of(), null, null, null, null).status())
                .isEqualTo(RoomRequestStatus.NEW);
    }

    @Test
    @DisplayName("aulas asignadas y edificios de derivación/devolución se pegan tal cual las resolvió el composer")
    void itemUsesAssignmentAndBuildingPieces() {
        List<AssignedClassroomDto> assigned = List.of(new AssignedClassroomDto(12L, 12, "Pabellón", 40));
        BuildingOptionDto derived = new BuildingOptionDto(3L, "Edificio Central");
        BuildingOptionDto returnedFrom = new BuildingOptionDto(4L, "Edificio Anexo");

        RoomRequestItemResponseDto dto =
                mapper.toDto(itemEntity(), null, List.of(), null, assigned, null, derived, returnedFrom, null);

        assertThat(dto.assignedClassrooms()).isEqualTo(assigned);
        assertThat(dto.derivedBuilding()).isEqualTo(derived);
        assertThat(dto.returnedFromBuilding()).isEqualTo(returnedFrom);
    }

    @Test
    @DisplayName("notifiedAt, derivedAt y returnedReason viajan del entity al DTO sin pasar por el composer")
    void itemCarriesResolutionTimestampsDirectlyFromEntity() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 10, 0);
        RoomRequestItem entity = RoomRequestItem.builder()
                .id(5L).position(1).classroomCount(1)
                .notifiedAt(now).derivedAt(now).returnedReason("no había proyector")
                .build();

        RoomRequestItemResponseDto dto =
                mapper.toDto(entity, null, List.of(), List.of(), List.of(), null, null, null, null);

        assertThat(dto.notifiedAt()).isEqualTo(now);
        assertThat(dto.derivedAt()).isEqualTo(now);
        assertThat(dto.returnedReason()).isEqualTo("no había proyector");
    }

    @Test
    @DisplayName("toRowDto: requiresSpecialAssignment, wasReturned y partiallyResolved son calculados")
    void rowDtoComputesSpecialAssignmentReturnedAndPartiallyResolved() {
        RoomRequestItem itemWithComputers = RoomRequestItem.builder()
                .id(5L).position(1).classroomCount(2)
                .requiresComputers(true)
                .build();

        RoomRequestItemRowDto dto = mapper.toRowDto(itemWithComputers, null, List.of(), null, 1);

        assertThat(dto.requiresSpecialAssignment()).isTrue();
        assertThat(dto.wasReturned()).isFalse();
        assertThat(dto.partiallyResolved()).isTrue();
        assertThat(dto.assignedClassroomCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("toRowDto: sin cómputo/software/examen con usuarios, no requiere asignación especial")
    void rowDtoWithoutSpecialRequirementsIsFalse() {
        RoomRequestItem plain = RoomRequestItem.builder().id(5L).position(1).classroomCount(1).build();

        RoomRequestItemRowDto dto = mapper.toRowDto(plain, null, List.of(), null, 1);

        assertThat(dto.requiresSpecialAssignment()).isFalse();
        assertThat(dto.partiallyResolved()).isFalse();
    }

    @Test
    @DisplayName("toRowDto: returnedFromBuildingId seteado marca wasReturned true")
    void rowDtoWasReturnedTrueWhenReturnedFromBuildingIdIsSet() {
        RoomRequestItem returned = RoomRequestItem.builder()
                .id(5L).position(1).classroomCount(1).returnedFromBuildingId(9L)
                .build();

        RoomRequestItemRowDto dto = mapper.toRowDto(returned, null, List.of(), null, 0);

        assertThat(dto.wasReturned()).isTrue();
        assertThat(dto.partiallyResolved()).isFalse();
    }

    private static RoomRequest requestEntity() {
        return RoomRequest.builder()
                .id(1L)
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(42L)
                .build();
    }

    private static RoomRequestItem itemEntity() {
        return RoomRequestItem.builder()
                .id(5L)
                .position(1)
                .commissionId(7L)
                .date(LocalDate.of(2026, 9, 1))
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(35)
                .classroomCount(1)
                .observations("Observación de prueba")
                .build();
    }
}
