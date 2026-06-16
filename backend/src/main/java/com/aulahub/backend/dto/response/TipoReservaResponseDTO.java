package com.aulahub.backend.dto.response;

import com.aulahub.backend.model.enums.TipoRol;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TipoReservaResponseDTO {
    private Long id;
    private Long facultadId;
    private String clave;
    private String nombre;
    private boolean activo; // necesario para que el frontend sepa si mostrarlo en el formulario de reserva
    private TipoRol rolMinimo; // rol mínimo que puede usar este tipo (PROFESOR por defecto)
    private int prioridad;     // mayor = se muestra primero al competir por un horario
}
