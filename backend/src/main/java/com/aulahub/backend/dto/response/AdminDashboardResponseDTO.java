package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// DTO de respuesta para el panel del ADMIN de facultad
// El ADMIN gestiona aulas específicas dentro de un turno (MATUTINO o VESPERTINO)
// Solo ve reservas y estadísticas de las aulas que tiene asignadas
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class AdminDashboardResponseDTO {

    private String nombreFacultad;       // Facultad a la que pertenece el admin
    private String turno;                // Turno que gestiona: MATUTINO o VESPERTINO
    private int totalAulasAsignadas;     // Cantidad de aulas bajo su gestión
    private int totalReservasPendientes; // Reservas de sus aulas que esperan revisión
    private int totalReservasAceptadas;  // Reservas de sus aulas actualmente aprobadas
    private int totalReservasHoy;        // Reservas programadas para hoy en sus aulas
}
