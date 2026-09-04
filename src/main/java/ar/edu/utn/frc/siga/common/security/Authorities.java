package ar.edu.utn.frc.siga.common.security;

public final class Authorities {

    private static final String PREFIX = "PERM_";

    private Authorities() {}

    public static String of(Permission permission) {
        return PREFIX + permission.name();
    }
}
