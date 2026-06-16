package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AulaResponseDTO {
    private Long id;
    private Long facultadId;
    private String codigo;
    private String nombre;
    private boolean activa;
    private Integer capacidad;
    private String imagenUrl;
}
