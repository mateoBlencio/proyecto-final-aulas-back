package ar.edu.utn.frc.siga.auth.service;

public interface PasswordResetService {

    void requestReset(String email, String ip);

    void reset(String rawToken, String newPassword, String ip);

    int purgeExpired();
}
