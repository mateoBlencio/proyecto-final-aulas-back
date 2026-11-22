package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.ChangePasswordRequest;
import ar.edu.utn.frc.siga.auth.dto.request.UpdateProfileRequest;
import ar.edu.utn.frc.siga.auth.dto.response.MeResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.TokenResponse;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;

public interface AccountService {

    MeResponseDto me(SecurityUser principal);

    MeResponseDto updateProfile(SecurityUser principal, UpdateProfileRequest request);

    TokenResponse changePassword(SecurityUser principal, ChangePasswordRequest request, String ip);
}
