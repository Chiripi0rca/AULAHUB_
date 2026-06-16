package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdatePerfilRequestDTO {

    // Opcional — si viene null o vacío se interpreta como querer quitar la foto
    @Size(max = 500, message = "La URL de la foto no puede exceder los 500 caracteres")
    private String fotoUrl;
}
