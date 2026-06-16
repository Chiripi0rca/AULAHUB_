package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificacionResponseDTO {
    private Long id;
    private Long usuarioId;
    private String titulo;
    private String mensaje;
    private boolean leido;
    private String tipo;
    private LocalDateTime createdAt;
}
