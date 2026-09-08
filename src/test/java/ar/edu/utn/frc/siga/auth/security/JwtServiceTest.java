package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 33 bytes, >= 256 bits

    private JwtProperties properties;
    private JwtService service;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessExpirationMinutes(20);
        service = new JwtService(properties);
    }

    @Test
    @DisplayName("generateAccessToken: el token generado es válido y trae el email")
    void generaTokenValidoConEmail() {
        String token = service.generateAccessToken("user@frc.utn.edu.ar");

        assertThat(service.isValid(token)).isTrue();
        Claims claims = service.parseClaims(token);
        assertThat(service.extractEmail(claims)).isEqualTo("user@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("isValid: token con firma alterada (otra clave) no es válido")
    void tokenConFirmaAlteradaNoEsValido() {
        String token = service.generateAccessToken("user@frc.utn.edu.ar");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(service.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isValid: token firmado con otra clave no es válido")
    void tokenFirmadoConOtraClaveNoEsValido() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("fedcba9876543210fedcba9876543210"); // otra clave, misma longitud mínima
        JwtService otherService = new JwtService(otherProperties);
        String token = otherService.generateAccessToken("user@frc.utn.edu.ar");

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("isValid: token expirado no es válido")
    void tokenExpiradoNoEsValido() {
        properties.setAccessExpirationMinutes(-1); // expiración en el pasado
        String token = service.generateAccessToken("user@frc.utn.edu.ar");

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("isValid: string arbitrario que no es un JWT no es válido")
    void stringArbitrarioNoEsValido() {
        assertThat(service.isValid("no-soy-un-jwt")).isFalse();
    }

    @Test
    @DisplayName("getAccessExpirationSeconds: convierte los minutos configurados a segundos")
    void getAccessExpirationSecondsConvierte() {
        assertThat(service.getAccessExpirationSeconds()).isEqualTo(20 * 60L);
    }
}
