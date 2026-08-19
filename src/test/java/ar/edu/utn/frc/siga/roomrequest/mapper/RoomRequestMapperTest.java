package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestMapper (aislado)")
class RoomRequestMapperTest {

    private final RoomRequestMapper mapper = new RoomRequestMapperImpl();

    @Nested
    @DisplayName("toEntity(CreateRoomRequestDto)")
    class HeaderToEntity {

        @Test
        @DisplayName("copia los campos propios de la cabecera")
        void copiesOwnFields() {
            RoomRequest entity = mapper.toEntity(headerDto());

            assertThat(entity.getType()).isEqualTo(RoomRequestType.PARTIAL_EXAM);
            assertThat(entity.getScope()).isEqualTo(AcademicScope.GRADO);
            assertThat(entity.getTeacherName()).isEqualTo("Ada Lovelace");
            assertThat(entity.getTeacherEmail()).isEqualTo("ada@frc.utn.edu.ar");
            assertThat(entity.getTeacherPhone()).isEqualTo("351-1234567");
            assertThat(entity.getSubjectId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("deja en null lo que no le corresponde poner")
        void leavesGeneratedFieldsNull() {
            RoomRequest entity = mapper.toEntity(headerDto());

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getGlpiTicketId()).isNull();
        }

        @Test
        @DisplayName("items queda como lista vacía, no null: lo pone el @Builder.Default de la entidad")
        void itemsDefaultToEmptyList() {
            assertThat(mapper.toEntity(headerDto()).getItems()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("la lista de items acepta addItem sobre lo que devolvió el mapper")
        void mappedEntityAcceptsItems() {
            RoomRequest entity = mapper.toEntity(headerDto());

            entity.addItem(mapper.toEntity(itemDto()));

            assertThat(entity.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getPosition()).isEqualTo(1));
        }

        @Test
        @DisplayName("null entra, null sale")
        void nullIsPassedThrough() {
            assertThat(mapper.toEntity((CreateRoomRequestDto) null)).isNull();
        }
    }

    @Nested
    @DisplayName("toEntity(CreateRoomRequestItemDto)")
    class ItemToEntity {

        @Test
        @DisplayName("copia los campos propios del pedido")
        void copiesOwnFields() {
            RoomRequestItem item = mapper.toEntity(itemDto());

            assertThat(item.getCommissionId()).isEqualTo(7L);
            assertThat(item.getDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(item.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(item.getEnrolled()).isEqualTo(30);
            assertThat(item.getEstimated()).isEqualTo(35);
            assertThat(item.getClassroomCount()).isEqualTo(1);
            assertThat(item.getCurrentClassroomId()).isEqualTo(10);
            assertThat(item.getObservations()).isEqualTo("Observación de prueba");
        }

        @Test
        @DisplayName("duration sale del rango del DTO, no de un campo propio")
        void derivesDurationFromRange() {
            assertThat(mapper.toEntity(itemDto()).getDuration()).isEqualTo(Duration.ofMinutes(120));
        }

        @Test
        @DisplayName("status arranca en PENDING: lo pone el @Builder.Default, no el mapper")
        void statusDefaultsToPending() {
            assertThat(mapper.toEntity(itemDto()).getStatus()).isEqualTo(RoomRequestStatus.PENDING);
        }

        @Test
        @DisplayName("preferences queda como lista vacía, no null")
        void preferencesDefaultToEmptyList() {
            assertThat(mapper.toEntity(itemDto()).getPreferences()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("el item mapeado acepta addPreferences y las numera desde 1")
        void mappedItemAcceptsPreferences() {
            RoomRequestItem item = mapper.toEntity(itemDto());

            item.addPreferences(List.of(11, 12));

            assertThat(item.getPreferences()).extracting("position").containsExactly(1, 2);
        }

        @Test
        @DisplayName("booleanos en null caen a false, que es lo que exige el NOT NULL de la columna")
        void nullBooleansDefaultToFalse() {
            RoomRequestItem item = mapper.toEntity(itemDtoWithNullBooleans());

            assertThat(item.getRequiresProjector()).isFalse();
            assertThat(item.getRequiresComputers()).isFalse();
        }

        @Test
        @DisplayName("requiresExamUsers se copia tal cual, incluido el null: distingue 'dijo que no' de 'no aplica'")
        void examUsersKeepsItsThreeStates() {
            assertThat(mapper.toEntity(itemDto(true)).getRequiresExamUsers()).isTrue();
            assertThat(mapper.toEntity(itemDto(false)).getRequiresExamUsers()).isFalse();
            assertThat(mapper.toEntity(itemDto(null)).getRequiresExamUsers()).isNull();
        }

        @Test
        @DisplayName("deja en null lo que asignan la cabecera o el flujo de decisión")
        void leavesAggregateFieldsNull() {
            RoomRequestItem item = mapper.toEntity(itemDto());

            assertThat(item.getId()).isNull();
            assertThat(item.getRequest()).isNull();
            assertThat(item.getPosition()).isNull();
            assertThat(item.getDecidedBy()).isNull();
            assertThat(item.getDecidedAt()).isNull();
            assertThat(item.getDecisionReason()).isNull();
        }

        @Test
        @DisplayName("null entra, null sale")
        void nullIsPassedThrough() {
            assertThat(mapper.toEntity((CreateRoomRequestItemDto) null)).isNull();
        }
    }

    @Nested
    @DisplayName("toDto")
    class ToDto {

        @Test
        @DisplayName("la cabecera se arma con la materia y los items que le pasa el composer")
        void headerUsesComposedPieces() {
            SubjectResponseDto subject = new SubjectResponseDto(42L, 101, "Análisis Matemático I", "ANUAL", null);
            RoomRequestItemResponseDto itemDto = mapper.toDto(itemEntity(), null, null, List.of());

            RoomRequestResponseDto dto = mapper.toDto(requestEntity(), subject, List.of(itemDto));

            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.type()).isEqualTo(RoomRequestType.PARTIAL_EXAM);
            assertThat(dto.teacherName()).isEqualTo("Ada Lovelace");
            assertThat(dto.subject()).isSameAs(subject);
            assertThat(dto.items()).containsExactly(itemDto);
        }

        @Test
        @DisplayName("sin materia ni comisión: la cabecera queda con subject en null")
        void nullSubjectIsAllowed() {
            RoomRequestResponseDto dto = mapper.toDto(requestEntity(), null, List.of());

            assertThat(dto.subject()).isNull();
            assertThat(dto.items()).isEmpty();
        }

        @Test
        @DisplayName("el pedido deriva endTime y durationMinutes de la duración guardada")
        void itemDerivesEndTimeAndDuration() {
            RoomRequestItemResponseDto dto = mapper.toDto(itemEntity(), null, null, List.of());

            assertThat(dto.startTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(dto.endTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(dto.durationMinutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("comisión y aulas se pegan tal cual las resolvió el composer")
        void itemUsesComposedPieces() {
            CommissionResponseDto commission = new CommissionResponseDto(7L, "3K1", 1, 3, null);
            ClassroomOptionDto current = new ClassroomOptionDto(10, "A10", "Pabellón");
            List<ClassroomOptionDto> preferred = List.of(new ClassroomOptionDto(11, "A11", "Pabellón"));

            RoomRequestItemResponseDto dto = mapper.toDto(itemEntity(), commission, current, preferred);

            assertThat(dto.commission()).isSameAs(commission);
            assertThat(dto.currentClassroom()).isSameAs(current);
            assertThat(dto.preferredClassrooms()).isEqualTo(preferred);
        }

        @Test
        @DisplayName("el estado del pedido viaja al DTO")
        void itemCarriesStatus() {
            assertThat(mapper.toDto(itemEntity(), null, null, List.of()).status())
                    .isEqualTo(RoomRequestStatus.PENDING);
        }
    }

    private static CreateRoomRequestDto headerDto() {
        return new CreateRoomRequestDto(RoomRequestType.PARTIAL_EXAM, AcademicScope.GRADO,
                "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567", 42L, List.of(itemDto()));
    }

    private static CreateRoomRequestItemDto itemDto() {
        return itemDto(null);
    }

    private static CreateRoomRequestItemDto itemDto(Boolean requiresExamUsers) {
        return new CreateRoomRequestItemDto(7L, LocalDate.of(2026, 9, 1),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 30, 35, 1, 10,
                true, false, null, requiresExamUsers, null, "Observación de prueba", List.of());
    }

    private static CreateRoomRequestItemDto itemDtoWithNullBooleans() {
        return new CreateRoomRequestItemDto(7L, LocalDate.of(2026, 9, 1),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 30, 35, 1, null,
                null, null, null, null, null, null, List.of());
    }

    private static RoomRequest requestEntity() {
        return RoomRequest.builder()
                .id(1L)
                .type(RoomRequestType.PARTIAL_EXAM)
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
                .enrolled(30)
                .estimated(35)
                .classroomCount(1)
                .currentClassroomId(10)
                .observations("Observación de prueba")
                .build();
    }
}
