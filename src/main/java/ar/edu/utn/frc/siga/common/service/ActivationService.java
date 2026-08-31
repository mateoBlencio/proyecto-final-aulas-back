package ar.edu.utn.frc.siga.common.service;

public interface ActivationService<ID> {

    void activate(ID id);

    void deactivate(ID id);
}
