package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MateriaRequestDTO {

    @NotNull(message = "La facultad es obligatoria")
    @Positive(message = "El ID de la facultad debe ser un número válido")
    private Long facultadId;

    @NotBlank(message = "El nombre de la materia es obligatorio")
    @Size(max = 255, message = "El nombre no puede ser mayor a 255 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El nombre solo puede contener letras y espacios")
    private String nombre;

    // activa se omite — toda materia nueva es activa por defecto (ver MateriaEntity)
}
