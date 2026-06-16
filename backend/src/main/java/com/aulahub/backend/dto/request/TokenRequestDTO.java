package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Cuerpo para registrar/eliminar el token de push de un dispositivo.
@Getter
@Setter
public class TokenRequestDTO {

    @NotBlank
    private String token;

    private String plataforma; // android | ios | web (opcional)
}
