package ar.edu.utn.frc.siga.common.util;

public final class Overcrowding {

    private Overcrowding() {
    }

    public static Integer by(Integer enrolled, Integer capacity) {
        if (enrolled == null || capacity == null) {
            return null;
        }
        return Math.max(0, enrolled - capacity);
    }
}
