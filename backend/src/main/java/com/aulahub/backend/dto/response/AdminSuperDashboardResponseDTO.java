package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// DTO de respuesta para el panel del SUPER_ADMIN
// El SUPER_ADMIN tiene visión completa de su facultad: todos los turnos, todas las aulas,
// todos los administradores y todas las reservas dentro de su facultad
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class AdminSuperDashboardResponseDTO {

    private String nombreFacultad;       // Facultad que gestiona el SUPER_ADMIN
    private int totalAulas;              // Total de aulas registradas en la facultad
    private int totalAulasActivas;       // Aulas disponibles actualmente
    private int totalReservasPendientes; // Reservas pendientes en toda la facultad
    private int totalReservasAceptadas;  // Reservas aprobadas activas en la facultad
    private int totalReservasHoy;        // Reservas programadas para hoy en la facultad
    private int totalUsuariosActivos;    // Usuarios con acceso activo en la facultad
}
