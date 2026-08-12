package ar.edu.utn.frc.siga.events.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum UniqueEventKind {
    PARCIAL, TRABAJO_PRACTICO, EXAMEN_FINAL, OTRO
}
