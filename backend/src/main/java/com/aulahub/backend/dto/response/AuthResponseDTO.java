package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;           // AccessToken JWT — caduca rápido
    private String refreshToken;    // RefreshToken — larga duración, se guarda en DB
    private String tipoToken;       // Siempre "Bearer" — lo fija el service, no el cliente
    private Long usuarioId;
    private String email;
    private String nombre;
    private List<String> roles;     // Un usuario puede tener múltiples roles
    private String fotoUrl;
}
