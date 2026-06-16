package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UsuarioResponseDTO {
    private Long id;
    private String email;
    private String nombre;
    private String fotoUrl;
    private boolean activo;
    private LocalDateTime createdAt;
}
