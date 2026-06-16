package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MateriaResponseDTO {
    private Long id;
    private Long facultadId;
    private String nombre;
    private boolean activa;
}
