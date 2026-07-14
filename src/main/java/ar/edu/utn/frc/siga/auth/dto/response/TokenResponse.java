package ar.edu.utn.frc.siga.auth.dto.response;

import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresInSeconds;

    private String refreshToken;

    private long refreshExpiresInSeconds;

    private String email;

    private Set<String> roles;
}
