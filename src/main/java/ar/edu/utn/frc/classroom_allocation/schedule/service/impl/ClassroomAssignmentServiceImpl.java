package ar.edu.utn.frc.classroom_allocation.schedule.service.impl;

import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.schedule.model.ClassroomAssignment;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.schedule.repository.ClassroomAssignmentRepository;
import ar.edu.utn.frc.classroom_allocation.schedule.service.ClassroomAssignmentService;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomAssignmentServiceImpl implements ClassroomAssignmentService {

    private final ClassroomAssignmentRepository assignmentRepository;

    @Override
    public Optional<ClassroomAssignment> findBySubjectCommissionAndClassroomAndTimeSlot(
            SubjectCommission subjectCommission, Classroom classroom, TimeSlot timeSlot) {
        return assignmentRepository.findBySubjectCommissionAndClassroomAndTimeSlot(
                subjectCommission, classroom, timeSlot);
    }

    @Override
    @Transactional
    public ClassroomAssignment save(ClassroomAssignment assignment) {
        return assignmentRepository.save(assignment);
    }
}
