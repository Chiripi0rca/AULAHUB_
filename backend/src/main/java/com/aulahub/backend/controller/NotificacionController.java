package com.aulahub.backend.controller;

import com.aulahub.backend.dto.response.NotificacionResponseDTO;
import com.aulahub.backend.dto.response.PageResponseDTO;
import com.aulahub.backend.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    // Lista las notificaciones del usuario paginadas, más recientes primero.
    // Solo el propio usuario puede consultar sus notificaciones.
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<PageResponseDTO<NotificacionResponseDTO>> listar(
            @PathVariable Long usuarioId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(notificacionService.listarPorUsuario(usuarioId, pageable)));
    }

    // Conteo de no leídas — alimenta el badge de la campana en el frontend.
    @GetMapping("/usuario/{usuarioId}/no-leidas")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Map<String, Integer>> contarNoLeidas(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(Map.of("noLeidas", notificacionService.contarNoLeidas(usuarioId)));
    }

    // Marca una notificación como leída — solo el propietario.
    @PutMapping("/{id}/leer")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> marcarLeido(@PathVariable Long id) {
        notificacionService.marcarLeido(id);
        return ResponseEntity.noContent().build();
    }

    // Marca todas las notificaciones del usuario como leídas — solo el propietario.
    @PutMapping("/usuario/{usuarioId}/leer-todas")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> marcarTodasLeidas(@PathVariable Long usuarioId) {
        notificacionService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }

    // Elimina una notificación — solo el propietario.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        notificacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
