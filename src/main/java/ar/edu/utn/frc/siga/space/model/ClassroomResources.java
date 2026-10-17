package ar.edu.utn.frc.siga.space.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public final class ClassroomResources {

    public static final String COMPUTERS = "Cantidad de PC";
    public static final String PROJECTOR = "Proyector";

    private ClassroomResources() {
    }
}
