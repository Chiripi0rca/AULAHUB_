package com.aulahub.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// DTO de respuesta para el panel del ADMIN_CENTRAL
// El ADMIN_CENTRAL tiene visión global de todo el sistema sin importar la facultad:
// puede ver lo que está pasando en cada facultad al mismo tiempo
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class AdminCentralDashboardResponseDTO {

    private int totalFacultades;           // Total de facultades registradas en el sistema
    private int totalAulasActivas;         // Total de aulas activas en todo el sistema
    private int totalUsuariosActivos;      // Total de usuarios activos en todo el sistema
    private int totalReservasPendientes;   // Reservas pendientes en todo el sistema
    private int totalReservasAceptadas;    // Reservas aprobadas activas en todo el sistema
    private int totalReservasHoy;          // Total de reservas programadas para hoy en todo el sistema
}
