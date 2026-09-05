package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
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
        RoomRequestItemResponseDto itemDto = mapper.toDto(itemEntity(), null, List.of());

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
        RoomRequestItemResponseDto dto = mapper.toDto(itemEntity(), null, List.of());

        assertThat(dto.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(dto.durationMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("día de dictado y estimado viajan al DTO; ya no hay enrolled ni aula actual")
    void itemCarriesDayOfWeekAndEstimated() {
        RoomRequestItem entity = RoomRequestItem.builder()
                .id(5L).position(1).commissionId(7L)
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(120))
                .estimated(35).classroomCount(1)
                .build();

        RoomRequestItemResponseDto dto = mapper.toDto(entity, null, List.of());

        assertThat(dto.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(dto.date()).isNull();
        assertThat(dto.estimated()).isEqualTo(35);
    }

    @Test
    @DisplayName("comisión y aulas de preferencia se pegan tal cual las resolvió el composer")
    void itemUsesComposedPieces() {
        CommissionResponseDto commission = new CommissionResponseDto(7L, "3K1", null);
        List<ClassroomOptionDto> preferred = List.of(new ClassroomOptionDto(11L, 11, "Pabellón"));

        RoomRequestItemResponseDto dto = mapper.toDto(itemEntity(), commission, preferred);

        assertThat(dto.commission()).isSameAs(commission);
        assertThat(dto.preferredClassrooms()).isEqualTo(preferred);
    }

    @Test
    @DisplayName("el estado del pedido viaja al DTO")
    void itemCarriesStatus() {
        assertThat(mapper.toDto(itemEntity(), null, List.of()).status())
                .isEqualTo(RoomRequestStatus.PENDING);
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
