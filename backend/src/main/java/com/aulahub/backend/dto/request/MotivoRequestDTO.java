package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MotivoRequestDTO {

    // Sin @Pattern — un motivo de rechazo puede incluir números, fechas, puntuación, etc.
    // Ej: "El Lab-3 tiene mantenimiento el 15/06, no estará disponible"
    @NotBlank(message = "El motivo del rechazo es obligatorio")
    @Size(max = 255, message = "El motivo debe tener entre 10 y 255 caracteres")
    private String motivo;
}
