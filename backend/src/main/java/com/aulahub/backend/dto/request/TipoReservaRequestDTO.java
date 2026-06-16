package com.aulahub.backend.dto.request;

import com.aulahub.backend.model.enums.TipoRol;
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
public class TipoReservaRequestDTO {

    @NotNull(message = "La facultad es obligatoria")
    @Positive(message = "El ID de la facultad debe ser un número válido")
    private Long facultadId;

    @NotBlank(message = "La clave del tipo de reserva es obligatoria")
    @Size(max = 50, message = "La clave no puede exceder los 50 caracteres")
    @Pattern(regexp = "^[A-Z_]+$", message = "La clave debe estar en mayúsculas (ej: CLASE, EXAMEN, TUTORIA)")
    private String clave;

    @NotBlank(message = "El nombre del tipo de reserva es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    // Rol mínimo que puede usar el tipo. Opcional — si no se envía, el service asume PROFESOR.
    // Al ser enum, un valor inválido produce un 400 automáticamente.
    private TipoRol rolMinimo;

    // Prioridad para ordenar solicitudes (mayor = primero). Opcional — si no se envía, queda 0.
    private Integer prioridad;

    // activo se omite — todo tipo de reserva nuevo es activo por defecto (ver TipoReservaEntity)
}
