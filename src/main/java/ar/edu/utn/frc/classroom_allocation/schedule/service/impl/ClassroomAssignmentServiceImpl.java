package ar.edu.utn.frc.classroom_allocation.schedule.service.impl;

import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.schedule.model.ClassroomAssignment;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.schedule.repository.ClassroomAssignmentRepository;
import ar.edu.utn.frc.classroom_allocation.schedule.service.ClassroomAssignmentService;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomAssignmentServiceImpl implements ClassroomAssignmentService {

    private final ClassroomAssignmentRepository assignmentRepository;

    @Override
    public Optional<ClassroomAssignment> findBySubjectCommissionAndClassroomAndTimeSlot(
            SubjectCommission subjectCommission, Classroom classroom, TimeSlot timeSlot) {
        log.debug("Finding ClassroomAssignment: scId={}, classroomId={}, timeSlotId={}",
                subjectCommission.getId(), classroom.getId(), timeSlot.getId());
        return assignmentRepository.findBySubjectCommissionAndClassroomAndTimeSlot(
                subjectCommission, classroom, timeSlot);
    }

    @Override
    @Transactional
    public ClassroomAssignment save(ClassroomAssignment assignment) {
        log.debug("Saving ClassroomAssignment: scId={}, classroomId={}",
                assignment.getSubjectCommission().getId(), assignment.getClassroom().getId());
        ClassroomAssignment saved = assignmentRepository.save(assignment);
        log.info("ClassroomAssignment saved: id={}", saved.getId());
        return saved;
    }
}
