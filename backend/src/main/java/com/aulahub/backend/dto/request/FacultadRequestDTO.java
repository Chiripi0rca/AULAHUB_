package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FacultadRequestDTO {

    @NotBlank(message = "La clave de la facultad es obligatoria")
    @Size(max = 20, message = "La clave no puede ser mayor a 20 caracteres")
    @Pattern(regexp = "^[A-Z]{2,10}$", message = "La clave debe estar en mayúsculas y sin espacios (ej: FIC, FCA)")
    private String clave;

    @NotBlank(message = "El nombre de la facultad es obligatorio")
    @Size(max = 255, message = "El nombre no puede ser mayor a 255 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El nombre solo puede contener letras y espacios")
    private String nombre;

    // activa se omite — toda facultad nueva es activa por defecto (ver FacultadEntity)
}
