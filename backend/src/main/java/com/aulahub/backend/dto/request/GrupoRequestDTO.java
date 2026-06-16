package com.aulahub.backend.dto.request;

import com.aulahub.backend.model.enums.Turno;
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
public class GrupoRequestDTO {

    @NotNull(message = "La facultad es obligatoria")
    @Positive(message = "El ID de la facultad debe ser un número válido")
    private Long facultadId;

    @NotBlank(message = "El nombre del grupo es obligatorio")
    @Size(max = 50, message = "El nombre no puede exceder los 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "El nombre solo puede contener letras, números y guiones (ej. 4-1)")
    private String nombre;

    // Turno del grupo (MATUTINO | VESPERTINO). Opcional: si viene null, el service
    // lo defaultea a MATUTINO. El enum valida el valor automáticamente.
    private Turno turno;
}
