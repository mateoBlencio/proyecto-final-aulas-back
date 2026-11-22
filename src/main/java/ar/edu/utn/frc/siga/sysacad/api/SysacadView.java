package ar.edu.utn.frc.siga.sysacad.api;

public enum SysacadView {
    EDIFICIOS("edificios"),
    AULAS("aulas"),
    ESPECIALIDADES("especialidades"),
    MATERIAS("materias"),
    COMISIONES("comisiones"),
    EVENTOS("eventos académicos"),
    ASIGNACIONES("horarios de aulas"),
    FERIADOS("feriados");

    private final String label;

    SysacadView(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
