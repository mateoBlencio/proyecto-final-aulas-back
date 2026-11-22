package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.AbstractDeactivationIntegrationTest;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

abstract class AbstractRoomRequestDeactivationIntegrationTest extends AbstractDeactivationIntegrationTest {

    static final LocalTime START = LocalTime.of(8, 0);

    @Autowired
    protected RoomRequestRepository roomRequestRepository;

    protected record ChangeRequest(Long itemId, Long occurrenceId) {}

    protected Occurrence occurrenceStartingAtEight(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        Long eventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(), START, 90,
                date, date, 30);
        return occurrenceRepository.findByEvent_Id(eventId).getFirst();
    }

    protected RoomRequestItem item(RoomRequestType type, IntegrationTestData.SubjectAndCommission sc,
            Long sourceEventId, LocalDate date, int classroomCount, Classroom... preferred) {
        RoomRequest request = RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(sc.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(sc.commissionId())
                .sourceRecurringEventId(sourceEventId)
                .date(date)
                .startTime(START)
                .duration(Duration.ofMinutes(90))
                .classroomCount(classroomCount)
                .build();
        request.addItem(item);
        int position = 1;
        for (Classroom classroom : preferred) {
            item.getPreferences().add(RoomPreference.builder().item(item).classroomId(classroom.getId())
                    .position(position++).build());
        }
        roomRequestRepository.save(request);
        return item;
    }

    protected ChangeRequest oneTimeChangeWithTwoClassrooms(LocalDate date) {
        var sc = testData.materiaYComision();
        Occurrence occurrence = occurrenceStartingAtEight(sc, date);
        RoomRequestItem item = item(RoomRequestType.ONE_TIME_ROOM_CHANGE, sc, occurrence.getEvent().getId(), date, 2);
        return new ChangeRequest(item.getId(), occurrence.getId());
    }

    protected ResultActions assign(Long itemId, Classroom... classrooms) throws Exception {
        String ids = List.of(classrooms).stream().map(c -> String.valueOf(c.getId()))
                .collect(Collectors.joining(","));
        return mockMvc.perform(post("/v1/room-requests/items/{id}/assign", itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"classroomIds\":[" + ids + "],\"reason\":\"\"}"));
    }

    protected ResultActions detail(Long itemId) throws Exception {
        return mockMvc.perform(get("/v1/room-requests/items/{id}", itemId));
    }
}
