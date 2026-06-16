package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenRequestDTO {

    @NotBlank(message = "El token de refresco es obligatorio")
    @Size(max = 500, message = "El token excede el tamaño permitido")
    private String refreshToken; // renombrado de "token" para coincidir con lo que manda el frontend
}
