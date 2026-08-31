package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.CursadoScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Red de seguridad OCP: agregar un {@link RoomRequestType} sin su handler tiene que romper acá. */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestHandlers (registro)")
class RoomRequestHandlersRegistryTest {

    @Mock
    private AcademicReferenceValidator academicReference;
    @Mock
    private ClassroomReferenceValidator classroomReference;
    @Mock
    private CursadoScheduleService cursadoSchedule;

    private List<RoomRequestTypeHandler> allHandlers() {
        return List.of(
                new OneTimeRoomChangeHandler(academicReference, classroomReference, cursadoSchedule),
                new RegularRoomChangeHandler(academicReference, classroomReference, cursadoSchedule),
                new PartialExamInClassHandler(academicReference, classroomReference, cursadoSchedule),
                new PartialExamOffScheduleHandler(academicReference, classroomReference, cursadoSchedule),
                new FinalExamHandler(academicReference, classroomReference, cursadoSchedule),
                new ConferenceHandler(academicReference, classroomReference, cursadoSchedule),
                new OtherHandler(academicReference, classroomReference, cursadoSchedule));
    }

    @Test
    @DisplayName("cada valor del enum tiene exactamente un handler")
    void everyTypeHasExactlyOneHandler() {
        RoomRequestHandlers handlers = new RoomRequestHandlers(allHandlers());

        for (RoomRequestType type : RoomRequestType.values()) {
            assertThat(handlers.forType(type)).as("handler de %s", type).isNotNull();
        }
    }

    @Test
    @DisplayName("falta un handler: falla al construir el registro")
    void missingHandlerFailsFast() {
        assertThatThrownBy(() -> new RoomRequestHandlers(
                List.of(new OtherHandler(academicReference, classroomReference, cursadoSchedule))))
                .isInstanceOf(IllegalStateException.class);
    }
}
