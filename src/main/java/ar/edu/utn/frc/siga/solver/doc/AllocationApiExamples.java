package ar.edu.utn.frc.siga.solver.doc;

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
                { "id": 301, "roomNumber": "301", "floor": 3, "capacity": 40, "available": true, "buildingId": 1, "buildingName": "Edif. Malvinas" },
                { "id": 302, "roomNumber": "302", "floor": 3, "capacity": 25, "available": true, "buildingId": 1, "buildingName": "Edif. Malvinas" },
                { "id": 802, "roomNumber": "802", "floor": 8, "capacity": 25, "available": true, "buildingId": 2, "buildingName": "Edif. Ing. Rubén Soro" }
              ],
              "parameters": {
                "timeLimitSeconds": 30,
                "pinnedAssignments": [{ "eventId": "GG-5K2", "classroomId": 301 }],
                "excludedClassroomIds": [803],
                "excludedBuildingNames": ["Edif. Ing. Rubén Soro"]
              }
            }""";
}