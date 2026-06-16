package com.aulahub.backend.dto.request;

import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.enums.Turno;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterRequestDTO {

    // ── Datos del usuario ──────────────────────────────────────────────────

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El nombre solo puede contener letras")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos no pueden exceder 100 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "Los apellidos solo pueden contener letras")
    private String apellidos;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo no es válido")
    @Size(max = 255, message = "El correo excede el tamaño permitido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 255, message = "La contraseña debe tener entre 8 y 255 caracteres")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
            message = "La contraseña debe contener al menos un número, una mayúscula, una minúscula y un carácter especial (@#$%^&+=!)"
    )
    private String password;

    // ── Rol asignado ───────────────────────────────────────────────────────

    // Tipo de rol que se le asigna al crear la cuenta
    // ADMIN_CENTRAL puede asignar cualquier tipo
    // SUPER_ADMIN solo puede asignar PROFESOR y ADMIN (validado en el service)
    @NotNull(message = "El tipo de rol es obligatorio")
    private TipoRol tipo;

    // Obligatorio cuando tipo = ADMIN o SUPER_ADMIN
    // Opcional cuando tipo = PROFESOR (puede dar en varias facultades, se agregan después con /api/roles)
    // Nulo cuando tipo = ADMIN_CENTRAL (tiene scope global, no pertenece a una facultad)
    // La validación condicional se hace en el service
    @Positive(message = "El ID de la facultad debe ser un número válido")
    private Long facultadId;

    // Solo aplica cuando tipo = ADMIN
    // Indica el turno que gestiona: MATUTINO o VESPERTINO
    // PROFESOR cubre ambos turnos por defecto — si se envía turno para PROFESOR se ignora
    // SUPER_ADMIN y ADMIN_CENTRAL tampoco tienen turno
    private Turno turno;

    // Solo aplica cuando tipo = ADMIN — IDs de las aulas que gestionará.
    private java.util.List<Long> aulaIds;
}
