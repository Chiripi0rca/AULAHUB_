package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.RolRequestDTO;
import com.aulahub.backend.dto.response.RolResponseDTO;
import com.aulahub.backend.service.RolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
public class RolController {

    private final RolService rolService;

    @GetMapping
    public ResponseEntity<List<RolResponseDTO>> listar() {
        return ResponseEntity.ok(rolService.listar());
    }

    // Devuelve los roles del usuario autenticado — accesible por cualquier rol
    // El frontend lo usa para filtrar qué facultades mostrar en la página de aulas
    @GetMapping("/mis-roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RolResponseDTO>> listarMisRoles() {
        return ResponseEntity.ok(rolService.listarMisRoles());
    }

    // Roles de un usuario específico
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<RolResponseDTO>> listarPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(rolService.listarPorUsuario(usuarioId));
    }

    // Roles de una facultad — SUPER_ADMIN ve solo ADMIN y PROFESOR, ADMIN_CENTRAL ve todos
    @GetMapping("/facultad/{facultadId}")
    public ResponseEntity<List<RolResponseDTO>> listarPorFacultad(@PathVariable Long facultadId) {
        return ResponseEntity.ok(rolService.listarPorFacultad(facultadId));
    }

    @PostMapping
    public ResponseEntity<RolResponseDTO> asignar(@RequestBody @Valid RolRequestDTO request) {
        return ResponseEntity.ok(rolService.asignar(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        rolService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // Quita una aula específica de un rol ADMIN sin revocar el rol completo
    @DeleteMapping("/{rolId}/aulas/{aulaId}")
    public ResponseEntity<Void> eliminarAula(@PathVariable Long rolId, @PathVariable Long aulaId) {
        rolService.eliminarAula(rolId, aulaId);
        return ResponseEntity.noContent().build();
    }
}
