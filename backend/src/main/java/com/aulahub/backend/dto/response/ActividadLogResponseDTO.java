package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class ActividadLogResponseDTO {

    private Long id;
    private Long usuarioId; // Agregamos este para que el front sepa quien es al momento de hacer click
    private String usuarioNombre;  // Nombre del usuario que realizó la acción
    private String accion;         // Ej: ACEPTAR_RESERVA, CREAR_AULA, ASIGNAR_ROL
    private String entidad;        // Tabla afectada: reserva, aula, usuario, materia…
    private Long entidadId;        // ID del registro afectado
    private String detalle;        // Descripción legible de lo que ocurrió
    private LocalDateTime createdAt;
}
