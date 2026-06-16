package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.CambiarPasswordRequestDTO;
import com.aulahub.backend.dto.request.ForgotPasswordRequestDTO;
import com.aulahub.backend.dto.request.LoginRequestDTO;
import com.aulahub.backend.dto.request.RefreshTokenRequestDTO;
import com.aulahub.backend.dto.request.RegisterRequestDTO;
import com.aulahub.backend.dto.request.ResetPasswordRequestDTO;
import com.aulahub.backend.dto.response.AuthResponseDTO;
import com.aulahub.backend.dto.response.UsuarioResponseDTO;
import com.aulahub.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Endpoint público — autentica y devuelve los tokens
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid LoginRequestDTO request,
                                                 HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, obtenerIp(http)));
    }

    // Endpoint público — renueva el accessToken usando el refreshToken
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@RequestBody @Valid RefreshTokenRequestDTO request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // Endpoint autenticado — cierra la sesión invalidando el refreshToken
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenRequestDTO request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    // Crea una nueva cuenta de usuario en el sistema
    // ADMIN_CENTRAL puede crear cualquier tipo de cuenta
    // SUPER_ADMIN solo puede crear cuentas de PROFESOR y ADMIN de su facultad
    @PostMapping("/registrar")
    @PreAuthorize("hasAnyAuthority('ADMIN_CENTRAL', 'SUPER_ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> registrar(@RequestBody @Valid RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    // Endpoint público — envía un email con el link de recuperación de contraseña.
    // Limitado por IP: es publico y cada solicitud dispara un correo.
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO request,
                                               HttpServletRequest http) {
        authService.enviarEmailRecuperacion(request, obtenerIp(http));
        return ResponseEntity.noContent().build();
    }

    // Endpoint público — cambia la contraseña usando el token del email de recuperación
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    // Endpoint autenticado — cambia la contraseña validando la actual (flujo directo desde Configuraciones)
    @PostMapping("/cambiar-password")
    public ResponseEntity<Void> cambiarPassword(@RequestBody @Valid CambiarPasswordRequestDTO request) {
        authService.cambiarPassword(request);
        return ResponseEntity.noContent().build();
    }

    // Obtiene la IP real del cliente — considera el header X-Forwarded-For si hay proxy/balanceador.
    private String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // la primera IP es la del cliente original
        }
        return request.getRemoteAddr();
    }
}
