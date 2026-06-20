package ar.edu.utn.frc.classroom_allocation.schedule.service;

import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.schedule.model.ClassroomAssignment;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import java.util.Optional;

public interface ClassroomAssignmentService {

    Optional<ClassroomAssignment> findBySubjectCommissionAndClassroomAndTimeSlot(
            SubjectCommission subjectCommission, Classroom classroom, TimeSlot timeSlot);

    ClassroomAssignment save(ClassroomAssignment assignment);
}
