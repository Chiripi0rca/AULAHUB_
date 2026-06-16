package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FacultadResponseDTO {
    private Long id;
    private String clave;
    private String nombre;
    private boolean activa;
}
