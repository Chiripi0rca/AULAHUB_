package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.Size;

@Getter
@Setter
public class UpdateUserRequestDTO {

    // Nombre completo del usuario — campo editable por SUPER_ADMIN y ADMIN_CENTRAL
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
    private String nombre;
}