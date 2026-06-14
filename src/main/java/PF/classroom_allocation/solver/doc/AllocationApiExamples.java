package PF.classroom_allocation.solver.doc;

public final class AllocationApiExamples {

    private AllocationApiExamples() {}

    public static final String PREVIEW_REQUEST = """
            {
              "events": [
                {
                  "id": "GG-5K2",
                  "type": "RECURRING",
                  "subject": "Gestión gerencial",
                  "section": "5K2",
                  "enrolled": 30,
                  "startTime": "08:00",
                  "durationMinutes": 90,
                  "dayOfWeek": "WEDNESDAY",
                  "startDate": "2026-03-10",
                  "endDate": "2026-07-05",
                  "date": "2026-04-14"
                },
                {
                  "id": "SG-5K2",
                  "type": "RECURRING",
                  "subject": "Sistemas de Gestión",
                  "section": "5K2",
                  "enrolled": 30,
                  "startTime": "08:05",
                  "durationMinutes": 90,
                  "dayOfWeek": "THURSDAY",
                  "startDate": "2026-03-10",
                  "endDate": "2026-07-05",
                  "date": "2026-04-14"
                }
              ],
              "classrooms": [
                { "id": "301", "name": "301", "building": "Edif. Malvinas", "capacityM2": 60.5 },
                { "id": "302", "name": "302", "building": "Edif. Malvinas", "capacityM2": 30.2 },
                { "id": "802", "name": "802", "building": "Edif. Ing. Rubén Soro", "capacityM2": 30.2 }
              ],
              "parameters": {
                "timeLimitSeconds": 30,
                "pinnedAssignments": [{ "eventId": "GG-5K2", "classroomId": "301" }],
                "excludedClassroomIds": ["803"],
                "excludedBuildingNames": ["Edif. Ing. Rubén Soro"]
              }
            }""";
}