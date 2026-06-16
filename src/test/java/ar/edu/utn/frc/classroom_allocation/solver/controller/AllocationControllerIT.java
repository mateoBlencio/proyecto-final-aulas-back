package ar.edu.utn.frc.classroom_allocation.solver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AllocationControllerIT {

    private static final String URL = "/api/v1/allocations/preview";

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    ObjectMapper objectMapper;

    private JsonNode post(String body) throws Exception {
        String responseBody = webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(responseBody);
    }

    private WebTestClient.ResponseSpec postRaw(String body) {
        return webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private JsonNode summary(JsonNode root) { return root.path("summary"); }

    // ─── TC-S-001 ─────────────────────────────────────────────────────────────

    @Test
    void tcS001_basicFeasibleAssignment() throws Exception {
        String body = """
            {
              "events": [
                {"id":"analisis-1c1-lunes","type":"RECURRING","subject":"Análisis Matemático I",
                 "section":"1C1","enrolled":80,"startTime":"18:15","durationMinutes":135,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-07-05"},
                {"id":"fisica-1c1-martes","type":"RECURRING","subject":"Física I",
                 "section":"1C1","enrolled":68,"startTime":"18:15","durationMinutes":135,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-04","endDate":"2024-07-05"},
                {"id":"algebra-1c1-miercoles","type":"RECURRING","subject":"Álgebra",
                 "section":"1C1","enrolled":66,"startTime":"20:40","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-04","endDate":"2024-07-05"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0},
                {"id":"aula-415","name":"415","building":"Edif. Ing.Inchaurrondo","capacityM2":85.0},
                {"id":"aula-220","name":"220","building":"Edif.Central","capacityM2":90.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(3);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(0);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        root.path("assignments").forEach(a ->
                assertThat(a.path("quality").asText()).isNotEqualTo("UNASSIGNED"));
    }

    // ─── TC-S-002 ─────────────────────────────────────────────────────────────

    @Test
    void tcS002_fiveEvents_fiveDays() throws Exception {
        String body = """
            {
              "events": [
                {"id":"quimica-lunes","type":"RECURRING","subject":"Química General",
                 "section":"1D1","enrolled":134,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"algebra-martes","type":"RECURRING","subject":"Álgebra",
                 "section":"1D1","enrolled":125,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"sistemas-miercoles","type":"RECURRING","subject":"Sistemas",
                 "section":"1D1","enrolled":138,"startTime":"09:40","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"analisis-jueves","type":"RECURRING","subject":"Análisis",
                 "section":"1D1","enrolled":137,"startTime":"10:25","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"pensamiento-viernes","type":"RECURRING","subject":"Pensamiento",
                 "section":"1D1","enrolled":141,"startTime":"11:20","durationMinutes":135,
                 "dayOfWeek":"FRIDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-225","name":"225","building":"Edif.Central","capacityM2":160.0},
                {"id":"aula-227","name":"227","building":"Edif.Central","capacityM2":145.0},
                {"id":"aula-229","name":"229","building":"Edif.Central","capacityM2":155.0},
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0}
              ],
              "parameters": {"timeLimitSeconds": 10}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(5);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(0);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
    }

    // ─── TC-S-003 ─────────────────────────────────────────────────────────────

    @Test
    void tcS003_twoEventsOneClassroom_sameSlot_partialSuccess() throws Exception {
        String body = """
            {
              "events": [
                {"id":"analisis-lunes","type":"RECURRING","subject":"Análisis",
                 "section":"1D3","enrolled":126,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"quimica-lunes","type":"RECURRING","subject":"Química",
                 "section":"1D4","enrolled":125,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-225","name":"225","building":"Edif.Central","capacityM2":160.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(1);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(1);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        assertThat(root.path("warnings")).hasSize(1);
        assertThat(root.path("warnings").get(0).path("code").asText()).isEqualTo("NO_CLASSROOM_AVAILABLE");
    }

    // ─── TC-S-004 ─────────────────────────────────────────────────────────────

    @Test
    void tcS004_conflictingEvents_assignedToDifferentClassrooms() throws Exception {
        String body = """
            {
              "events": [
                {"id":"fisica-jueves","type":"RECURRING","subject":"Física I",
                 "section":"1C2","enrolled":165,"startTime":"10:25","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"ingcivil-jueves","type":"RECURRING","subject":"Ing. Civil",
                 "section":"1C2","enrolled":153,"startTime":"11:00","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-06-27"}
              ],
              "classrooms": [
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0},
                {"id":"aula-229","name":"229","building":"Edif.Central","capacityM2":155.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        String room1 = root.path("assignments").get(0).path("classroom").path("id").asText();
        String room2 = root.path("assignments").get(1).path("classroom").path("id").asText();
        assertThat(room1).isNotEqualTo(room2);
    }

    // ─── TC-S-005 ─────────────────────────────────────────────────────────────

    @Test
    void tcS005_sameTimeAndDay_disjointDateRanges_canShareClassroom() throws Exception {
        String body = """
            {
              "events": [
                {"id":"ingcivil-primer","type":"RECURRING","subject":"Ing. Civil",
                 "section":"1C3","enrolled":72,"startTime":"13:15","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-06","endDate":"2024-06-26"},
                {"id":"ingcivil-segundo","type":"RECURRING","subject":"Ing. Civil",
                 "section":"1C3-B","enrolled":70,"startTime":"13:15","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-08-07","endDate":"2024-11-27"}
              ],
              "classrooms": [
                {"id":"aula-410","name":"410","building":"Edif. Ing.Inchaurrondo","capacityM2":110.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(2);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        assertThat(root.path("assignments").get(0).path("classroom").path("id").asText())
                .isEqualTo("aula-410");
        assertThat(root.path("assignments").get(1).path("classroom").path("id").asText())
                .isEqualTo("aula-410");
    }

    // ─── TC-S-006 ─────────────────────────────────────────────────────────────

    @Test
    void tcS006_fourEvents_twoClassrooms_sameSlot_partialSuccess() throws Exception {
        String body = """
            {
              "events": [
                {"id":"ev-lun-1","type":"RECURRING","subject":"Física","section":"1D1",
                 "enrolled":123,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"ev-lun-2","type":"RECURRING","subject":"Química","section":"1D2",
                 "enrolled":91,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"ev-lun-3","type":"RECURRING","subject":"Álgebra","section":"1D3",
                 "enrolled":130,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"ev-lun-4","type":"RECURRING","subject":"Ing. y Sociedad","section":"1D4",
                 "enrolled":119,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-225","name":"225","building":"Edif.Central","capacityM2":160.0},
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(2);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(2);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        assertThat(root.path("warnings")).hasSize(2);
    }

    // ─── TC-S-007 ─────────────────────────────────────────────────────────────

    @Test
    void tcS007_pinnedAssignment_honored() throws Exception {
        String body = """
            {
              "events": [
                {"id":"quimica-1c1","type":"RECURRING","subject":"Química",
                 "section":"1C1","enrolled":70,"startTime":"19:00","durationMinutes":90,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-06","endDate":"2024-11-27"},
                {"id":"fisica-1c1","type":"RECURRING","subject":"Física",
                 "section":"1C1","enrolled":68,"startTime":"17:20","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0},
                {"id":"aula-415","name":"415","building":"Edif. Ing.Inchaurrondo","capacityM2":85.0},
                {"id":"aula-220","name":"220","building":"Edif.Central","capacityM2":90.0}
              ],
              "parameters": {
                "timeLimitSeconds": 5,
                "pinnedAssignments": [{"eventId":"quimica-1c1","classroomId":"aula-513"}]
              }
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        for (JsonNode a : root.path("assignments")) {
            if (a.path("event").path("id").asText().equals("quimica-1c1")) {
                assertThat(a.path("classroom").path("id").asText()).isEqualTo("aula-513");
            }
        }
    }

    // ─── TC-S-008 ─────────────────────────────────────────────────────────────

    @Test
    void tcS008_excludedClassrooms_notUsed() throws Exception {
        String body = """
            {
              "events": [
                {"id":"analisis-1c1","type":"RECURRING","subject":"Análisis",
                 "section":"1C1","enrolled":80,"startTime":"21:35","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"fisica-1c3","type":"RECURRING","subject":"Física",
                 "section":"1C3","enrolled":83,"startTime":"15:40","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-06","endDate":"2024-11-27"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0},
                {"id":"aula-524","name":"524","building":"Edif. Dr. Gallardo","capacityM2":75.0},
                {"id":"aula-224","name":"224","building":"Edif.Central","capacityM2":85.0},
                {"id":"aula-220","name":"220","building":"Edif.Central","capacityM2":90.0}
              ],
              "parameters": {
                "timeLimitSeconds": 5,
                "excludedClassroomIds": ["aula-513"],
                "excludedBuildingNames": ["Edif. Dr. Gallardo"]
              }
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        for (JsonNode a : root.path("assignments")) {
            String roomId = a.path("classroom").path("id").asText();
            assertThat(roomId).isNotIn("aula-513", "aula-524");
        }
    }

    // ─── TC-S-009 ─────────────────────────────────────────────────────────────

    @Test
    void tcS009_twoUnique_sameDateAndTime_oneClassroom_partialSuccess() throws Exception {
        String body = """
            {
              "events": [
                {"id":"examen-analisis","type":"UNIQUE","subject":"Análisis",
                 "section":"1C1","enrolled":80,"startTime":"08:00","durationMinutes":180,
                 "date":"2024-07-23"},
                {"id":"examen-quimica","type":"UNIQUE","subject":"Química",
                 "section":"1D1","enrolled":134,"startTime":"08:00","durationMinutes":180,
                 "date":"2024-07-23"}
              ],
              "classrooms": [
                {"id":"aula-227","name":"227","building":"Edif.Central","capacityM2":145.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(1);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(1);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        assertThat(root.path("warnings")).hasSize(1);
    }

    // ─── TC-S-010 ─────────────────────────────────────────────────────────────

    @Test
    void tcS010_twoUnique_sameDate_differentTimes_shareClassroom() throws Exception {
        String body = """
            {
              "events": [
                {"id":"examen-analisis-morning","type":"UNIQUE","subject":"Análisis",
                 "section":"1C1","enrolled":80,"startTime":"08:00","durationMinutes":120,
                 "date":"2024-07-23"},
                {"id":"examen-quimica-afternoon","type":"UNIQUE","subject":"Química",
                 "section":"1D1","enrolled":90,"startTime":"14:00","durationMinutes":120,
                 "date":"2024-07-23"}
              ],
              "classrooms": [
                {"id":"aula-220","name":"220","building":"Edif.Central","capacityM2":90.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(2);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
    }

    // ─── TC-S-011 ─────────────────────────────────────────────────────────────

    @Test
    void tcS011_unique_conflictsWithRecurring_differentClassrooms() throws Exception {
        String body = """
            {
              "events": [
                {"id":"clase-analisis","type":"RECURRING","subject":"Análisis",
                 "section":"1D1","enrolled":137,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"examen-especial","type":"UNIQUE","subject":"Física",
                 "section":"1D3","enrolled":141,"startTime":"08:00","durationMinutes":90,
                 "date":"2024-05-09"}
              ],
              "classrooms": [
                {"id":"aula-225","name":"225","building":"Edif.Central","capacityM2":160.0},
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        String room1 = root.path("assignments").get(0).path("classroom").path("id").asText();
        String room2 = root.path("assignments").get(1).path("classroom").path("id").asText();
        assertThat(room1).isNotEqualTo(room2);
    }

    // ─── TC-S-012 ─────────────────────────────────────────────────────────────

    @Test
    void tcS012_softOptimization_prefersSmallRoom() throws Exception {
        String body = """
            {
              "events": [
                {"id":"ingcivil-jueves","type":"RECURRING","subject":"Ing. Civil",
                 "section":"1C1","enrolled":45,"startTime":"17:20","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-06-27"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0},
                {"id":"aula-229","name":"229","building":"Edif.Central","capacityM2":155.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        String assigned = root.path("assignments").get(0).path("classroom").path("id").asText();
        assertThat(assigned).isEqualTo("aula-513");
    }

    // ─── TC-S-013 ─────────────────────────────────────────────────────────────

    @Test
    void tcS013_forcedOvercrowding_poorQuality() throws Exception {
        String body = """
            {
              "events": [
                {"id":"algebra-masiva","type":"RECURRING","subject":"Álgebra",
                 "section":"1D3","enrolled":200,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-225","name":"225","building":"Edif.Central","capacityM2":160.0},
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        assertThat(summary(root).path("softScore").asInt()).isLessThan(0);
        assertThat(root.path("assignments").get(0).path("quality").asText()).isEqualTo("POOR");
        String assigned = root.path("assignments").get(0).path("classroom").path("id").asText();
        assertThat(assigned).isEqualTo("aula-401");
    }

    // ─── TC-S-014 ─────────────────────────────────────────────────────────────

    @Test
    void tcS014_cascadingConflicts_threeEvents_twoRooms() throws Exception {
        String body = """
            {
              "events": [
                {"id":"ev-A","type":"RECURRING","subject":"Química","section":"1C1",
                 "enrolled":70,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"ev-B","type":"RECURRING","subject":"Física","section":"1D1",
                 "enrolled":125,"startTime":"09:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"ev-C","type":"RECURRING","subject":"Análisis","section":"1D2",
                 "enrolled":68,"startTime":"10:00","durationMinutes":90,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0},
                {"id":"aula-220","name":"220","building":"Edif.Central","capacityM2":90.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(3);
    }

    // ─── TC-S-015 ─────────────────────────────────────────────────────────────

    @Test
    void tcS015_largeScale_tenEvents_sixRooms() throws Exception {
        String body = """
            {
              "events": [
                {"id":"fisica-1d1-lunes","type":"RECURRING","subject":"Física","section":"1D1",
                 "enrolled":125,"startTime":"09:40","durationMinutes":225,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"algebra-1d4-martes-am","type":"RECURRING","subject":"Álgebra","section":"1D4",
                 "enrolled":126,"startTime":"10:25","durationMinutes":90,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"quimica-1d4-martes-am","type":"RECURRING","subject":"Química","section":"1D4",
                 "enrolled":125,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"analisis-1d3-miercoles","type":"RECURRING","subject":"Análisis","section":"1D3",
                 "enrolled":126,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"quimica-1d3-miercoles","type":"RECURRING","subject":"Química","section":"1D3",
                 "enrolled":127,"startTime":"10:25","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"sistemas-1d3-viernes","type":"RECURRING","subject":"Sistemas","section":"1D3",
                 "enrolled":151,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"FRIDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"analisis-1d4-miercoles","type":"RECURRING","subject":"Análisis","section":"1D4",
                 "enrolled":125,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"fisica-1d4-jueves","type":"RECURRING","subject":"Física","section":"1D4",
                 "enrolled":123,"startTime":"08:00","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"informatica-1d4-jueves","type":"RECURRING","subject":"Informática","section":"1D4",
                 "enrolled":130,"startTime":"10:25","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-04","endDate":"2024-11-29"},
                {"id":"pensamiento-1d3-martes","type":"RECURRING","subject":"Pensamiento","section":"1D3",
                 "enrolled":157,"startTime":"11:20","durationMinutes":135,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-206","name":"206","building":"Edif.Central","capacityM2":125.0},
                {"id":"aula-225","name":"225","building":"Edif.Central","capacityM2":160.0},
                {"id":"aula-227","name":"227","building":"Edif.Central","capacityM2":145.0},
                {"id":"aula-229","name":"229","building":"Edif.Central","capacityM2":155.0},
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0},
                {"id":"aula-400","name":"400","building":"Edif. Ing.Inchaurrondo","capacityM2":155.0}
              ],
              "parameters": {"timeLimitSeconds": 20}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(10);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(0);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
    }

    // ─── TC-S-016 ─────────────────────────────────────────────────────────────

    @Test
    void tcS016_pinnedPlusConflictingFreeEvent() throws Exception {
        String body = """
            {
              "events": [
                {"id":"quimica-pinned","type":"RECURRING","subject":"Química","section":"1C2",
                 "enrolled":122,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-05","endDate":"2024-11-26"},
                {"id":"analisis-free","type":"RECURRING","subject":"Análisis","section":"1C2",
                 "enrolled":164,"startTime":"09:00","durationMinutes":90,
                 "dayOfWeek":"TUESDAY","startDate":"2024-03-05","endDate":"2024-11-26"}
              ],
              "classrooms": [
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0},
                {"id":"aula-229","name":"229","building":"Edif.Central","capacityM2":155.0}
              ],
              "parameters": {
                "timeLimitSeconds": 5,
                "pinnedAssignments": [{"eventId":"quimica-pinned","classroomId":"aula-401"}]
              }
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
        for (JsonNode a : root.path("assignments")) {
            if (a.path("event").path("id").asText().equals("quimica-pinned")) {
                assertThat(a.path("classroom").path("id").asText()).isEqualTo("aula-401");
            }
            if (a.path("event").path("id").asText().equals("analisis-free")) {
                assertThat(a.path("classroom").path("id").asText()).isEqualTo("aula-229");
            }
        }
    }

    // ─── TC-S-017 — Duplicate event IDs ──────────────────────────────────────

    @Test
    void tcS017_duplicateEventId_returns400() {
        String body = """
            {
              "events": [
                {"id":"evento-duplicado","type":"RECURRING","subject":"Física","section":"1C1",
                 "enrolled":68,"startTime":"17:20","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"evento-duplicado","type":"RECURRING","subject":"Química","section":"1C1",
                 "enrolled":70,"startTime":"19:00","durationMinutes":90,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-06","endDate":"2024-11-27"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ]
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-018 — Duplicate classroom IDs ──────────────────────────────────

    @Test
    void tcS018_duplicateClassroomId_returns400() {
        String body = """
            {
              "events": [
                {"id":"ev-1","type":"RECURRING","subject":"Análisis","section":"1C1",
                 "enrolled":80,"startTime":"18:15","durationMinutes":135,
                 "dayOfWeek":"MONDAY","startDate":"2024-03-04","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-dup","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0},
                {"id":"aula-dup","name":"513B","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ]
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-019 — Pinned non-existent eventId ───────────────────────────────

    @Test
    void tcS019_pinnedNonExistentEventId_returns400() {
        String body = """
            {
              "events": [
                {"id":"ev-real","type":"RECURRING","subject":"Física","section":"1C1",
                 "enrolled":68,"startTime":"17:20","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ],
              "parameters": {
                "pinnedAssignments": [{"eventId":"ev-fantasma","classroomId":"aula-513"}]
              }
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-020 — Pinned classroom also excluded ────────────────────────────

    @Test
    void tcS020_pinnedAndExcluded_returns400() {
        String body = """
            {
              "events": [
                {"id":"ev-1","type":"RECURRING","subject":"Física","section":"1C1",
                 "enrolled":68,"startTime":"17:20","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ],
              "parameters": {
                "pinnedAssignments": [{"eventId":"ev-1","classroomId":"aula-513"}],
                "excludedClassroomIds": ["aula-513"]
              }
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-021 — RECURRING missing dayOfWeek ───────────────────────────────

    @Test
    void tcS021_recurringMissingDayOfWeek_returns400() {
        String body = """
            {
              "events": [
                {"id":"ev-incompleto","type":"RECURRING","subject":"Física","section":"1C1",
                 "enrolled":68,"startTime":"17:20","durationMinutes":90,
                 "startDate":"2024-03-07","endDate":"2024-11-28"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ]
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-022 — RECURRING endDate before startDate ───────────────────────

    @Test
    void tcS022_recurringEndBeforeStart_returns400() {
        String body = """
            {
              "events": [
                {"id":"ev-fechas-inversas","type":"RECURRING","subject":"Química","section":"1C1",
                 "enrolled":70,"startTime":"19:00","durationMinutes":90,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-11-27","endDate":"2024-03-06"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ]
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-023 — UNIQUE missing date ──────────────────────────────────────

    @Test
    void tcS023_uniqueMissingDate_returns400() {
        String body = """
            {
              "events": [
                {"id":"examen-sin-fecha","type":"UNIQUE","subject":"Análisis","section":"1C1",
                 "enrolled":80,"startTime":"08:00","durationMinutes":180}
              ],
              "classrooms": [
                {"id":"aula-227","name":"227","building":"Edif.Central","capacityM2":145.0}
              ]
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-024 — Empty events list ─────────────────────────────────────────

    @Test
    void tcS024_emptyEventsList_returns400() {
        String body = """
            {
              "events": [],
              "classrooms": [
                {"id":"aula-227","name":"227","building":"Edif.Central","capacityM2":145.0}
              ]
            }""";

        postRaw(body).expectStatus().isBadRequest();
    }

    // ─── TC-S-025 — Adjacent time slots ──────────────────────────────────────

    @Test
    void tcS025_adjacentSlots_canShareClassroom() throws Exception {
        String body = """
            {
              "events": [
                {"id":"quimica-turno1","type":"RECURRING","subject":"Química","section":"1C1",
                 "enrolled":70,"startTime":"19:00","durationMinutes":90,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-06","endDate":"2024-11-27"},
                {"id":"fisica-turno2","type":"RECURRING","subject":"Física","section":"1C1",
                 "enrolled":68,"startTime":"20:30","durationMinutes":90,
                 "dayOfWeek":"WEDNESDAY","startDate":"2024-03-06","endDate":"2024-11-27"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0}
              ],
              "parameters": {"timeLimitSeconds": 5}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(2);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
    }

    // ─── TC-S-026 — All classrooms excluded ──────────────────────────────────

    @Test
    void tcS026_allClassroomsExcluded_partialSuccess() throws Exception {
        String body = """
            {
              "events": [
                {"id":"ev-sin-aula","type":"RECURRING","subject":"Ing. y Sociedad","section":"1D1",
                 "enrolled":120,"startTime":"09:40","durationMinutes":90,
                 "dayOfWeek":"FRIDAY","startDate":"2024-03-08","endDate":"2024-11-29"}
              ],
              "classrooms": [
                {"id":"aula-513","name":"513","building":"Edif. Dr. Gallardo","capacityM2":80.0},
                {"id":"aula-524","name":"524","building":"Edif. Dr. Gallardo","capacityM2":75.0}
              ],
              "parameters": {
                "timeLimitSeconds": 5,
                "excludedBuildingNames": ["Edif. Dr. Gallardo"]
              }
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(0);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(1);
        assertThat(root.path("warnings")).hasSize(1);
        assertThat(root.path("warnings").get(0).path("code").asText()).isEqualTo("NO_CLASSROOM_AVAILABLE");
    }

    // ─── TC-S-027 — Evening + morning shifts ─────────────────────────────────

    @Test
    void tcS027_fullWeekday_morningAndEvening_sixEvents() throws Exception {
        String body = """
            {
              "events": [
                {"id":"quimica-1d4-jue-am","type":"RECURRING","subject":"Química","section":"1D4",
                 "enrolled":125,"startTime":"08:00","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"informatica-1d4-jue-am","type":"RECURRING","subject":"Informática","section":"1D4",
                 "enrolled":130,"startTime":"10:25","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"analisis-1c1-jue-tarde","type":"RECURRING","subject":"Análisis","section":"1C1",
                 "enrolled":80,"startTime":"17:20","durationMinutes":90,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"fisica-1c1-jue-tarde","type":"RECURRING","subject":"Física","section":"1C1",
                 "enrolled":68,"startTime":"19:00","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"analisis-1d2-jue-noche","type":"RECURRING","subject":"Análisis","section":"1D2",
                 "enrolled":68,"startTime":"18:15","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"},
                {"id":"sistemas-1d2-jue-noche","type":"RECURRING","subject":"Sistemas","section":"1D2",
                 "enrolled":74,"startTime":"20:40","durationMinutes":135,
                 "dayOfWeek":"THURSDAY","startDate":"2024-03-07","endDate":"2024-11-28"}
              ],
              "classrooms": [
                {"id":"aula-206","name":"206","building":"Edif.Central","capacityM2":125.0},
                {"id":"aula-227","name":"227","building":"Edif.Central","capacityM2":145.0},
                {"id":"aula-401","name":"401","building":"Edif. Ing.Inchaurrondo","capacityM2":165.0}
              ],
              "parameters": {"timeLimitSeconds": 15}
            }""";

        JsonNode root = post(body);
        assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(summary(root).path("assigned").asInt()).isEqualTo(6);
        assertThat(summary(root).path("unassigned").asInt()).isEqualTo(0);
        assertThat(summary(root).path("hardScore").asInt()).isEqualTo(0);
    }
}
