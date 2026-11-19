package ar.edu.utn.frc.siga.academic.service;

import org.springframework.modulith.NamedInterface;

/** Visible names of academic records, shared by the modules that label them. */
@NamedInterface("api")
public final class AcademicLabels {

    private AcademicLabels() {
    }

    /** "Física I (2K3)"; only the subject, only the code, or null when both are blank. */
    public static String subjectCommission(String subjectName, String courseCode) {
        boolean hasSubject = subjectName != null && !subjectName.isBlank();
        boolean hasCode = courseCode != null && !courseCode.isBlank();
        if (hasSubject && hasCode) {
            return subjectName + " (" + courseCode + ")";
        }
        if (hasSubject) {
            return subjectName;
        }
        return hasCode ? courseCode : null;
    }
}
