package com.aulahub.backend.controller;

import com.aulahub.backend.dto.response.AdminCentralDashboardResponseDTO;
import com.aulahub.backend.dto.response.AdminDashboardResponseDTO;
import com.aulahub.backend.dto.response.AdminSuperDashboardResponseDTO;
import com.aulahub.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // Panel del ADMIN de facultad
    // Ve sus aulas asignadas, reservas pendientes en su turno y estadísticas de su turno
    @GetMapping("/admin/{usuarioId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AdminDashboardResponseDTO> panelAdmin(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(dashboardService.obtenerPanelAdmin(usuarioId));
    }

    // Panel del SUPER_ADMIN
    // Ve todo lo que pasa dentro de su facultad: todas las aulas, todos los turnos y todas las reservas
    @GetMapping("/super-admin/{usuarioId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<AdminSuperDashboardResponseDTO> panelSuperAdmin(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(dashboardService.obtenerPanelSuperAdmin(usuarioId));
    }

    // Panel del ADMIN_CENTRAL
    // Ve el estado global de todas las facultades del sistema al mismo tiempo
    @GetMapping("/admin-central")
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<AdminCentralDashboardResponseDTO> panelAdminCentral() {
        return ResponseEntity.ok(dashboardService.obtenerPanelAdminCentral());
    }
}
