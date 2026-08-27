package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Subject;

record SubjectKey(Integer code, Long studyPlanId) {
    static SubjectKey of(Subject subject) {
        return new SubjectKey(subject.getCode(), subject.getStudyPlan().getId());
    }
}
