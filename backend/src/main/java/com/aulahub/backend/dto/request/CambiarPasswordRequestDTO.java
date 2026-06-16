package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body de POST /api/auth/cambiar-password — cambio de contraseña del usuario autenticado.
@Getter
@Setter
@NoArgsConstructor
public class CambiarPasswordRequestDTO {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, max = 255, message = "La contraseña debe tener entre 8 y 255 caracteres")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
            message = "La contraseña debe contener al menos un número, una mayúscula, una minúscula y un carácter especial (@#$%^&+=!)"
    )
    private String nuevaPassword;
}
