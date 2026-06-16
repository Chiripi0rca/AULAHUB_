package com.aulahub.backend.dto.response;

import com.aulahub.backend.model.enums.Turno;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GrupoResponseDTO {
    private Long id;
    private Long facultadId;
    private String nombre;
    private boolean activo;
    private Turno turno; // MATUTINO | VESPERTINO — Jackson lo serializa como string
}
