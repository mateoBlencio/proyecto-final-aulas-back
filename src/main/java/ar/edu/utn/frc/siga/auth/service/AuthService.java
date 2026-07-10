package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.LoginRequest;
import ar.edu.utn.frc.siga.auth.dto.request.RefreshTokenRequest;
import ar.edu.utn.frc.siga.auth.dto.response.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    TokenResponse login(LoginRequest request, HttpServletRequest httpRequest);

    TokenResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);
}
