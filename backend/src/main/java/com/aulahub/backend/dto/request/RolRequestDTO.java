package com.aulahub.backend.dto.request;

import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.enums.Turno;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RolRequestDTO {

    @NotNull(message = "El usuario es obligatorio")
    @Positive(message = "El ID del usuario debe ser un número válido")
    private Long usuarioId;

    @NotNull(message = "El tipo de rol es obligatorio")
    private TipoRol tipo; // PROFESOR | ADMIN | SUPER_ADMIN | ADMIN_CENTRAL

    // Opcional — solo aplica para ADMIN (turno que gestiona: MATUTINO o VESPERTINO)
    // SUPER_ADMIN y ADMIN_CENTRAL no tienen turno específico
    private Turno turno;

    // Obligatorio para ADMIN y SUPER_ADMIN — facultad a la que pertenece el rol
    // Nulo para ADMIN_CENTRAL (scope global)
    @Positive(message = "El ID de la facultad debe ser un número válido")
    private Long facultadId;

    // Opcional — aulas que puede gestionar el ADMIN
    // SUPER_ADMIN gestiona todas las aulas de su facultad, no necesita lista
    private List<@NotNull(message = "El ID del aula no puede ser nulo")
                 @Positive(message = "El ID de cada aula debe ser un número válido") Long> aulaIds;
}
