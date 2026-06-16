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
public class AulaRequestDTO {

    @NotNull(message = "La facultad es obligatoria")
    @Positive(message = "El ID de la facultad debe ser un número válido")
    private Long facultadId;

    @NotBlank(message = "El código del aula es obligatorio")
    @Size(max = 50, message = "El código no puede ser mayor a 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "El código solo puede contener letras, números y guiones (ej: labA, LAB-01)")
    private String codigo;

    @NotBlank(message = "El nombre del aula es obligatorio")
    @Size(max = 255, message = "El nombre no puede ser mayor a 255 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]+$", message = "El nombre contiene caracteres no permitidos")
    private String nombre;

    // Capacidad máxima — opcional, null si no se especifica
    @Positive(message = "La capacidad debe ser un número positivo")
    private Integer capacidad;

    // activa se omite — toda aula nueva es activa por defecto (ver AulaEntity)
}
