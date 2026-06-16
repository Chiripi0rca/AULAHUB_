package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.AsignarGrupoMateriasRequestDTO;
import com.aulahub.backend.dto.request.AsignarMateriasRequestDTO;
import com.aulahub.backend.dto.request.UpdatePerfilRequestDTO;
import com.aulahub.backend.dto.request.UpdateUserRequestDTO;
import com.aulahub.backend.dto.response.AsignacionMaestroResponseDTO;
import com.aulahub.backend.dto.response.MateriaResponseDTO;
import com.aulahub.backend.dto.response.UsuarioResponseDTO;
import com.aulahub.backend.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    // Lista todos los usuarios del sistema — solo SUPER_ADMIN y ADMIN_CENTRAL
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    // Actualiza el nombre de un usuario — solo SUPER_ADMIN y ADMIN_CENTRAL
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<UsuarioResponseDTO> actualizar(
            @PathVariable Long id, @RequestBody @Valid UpdateUserRequestDTO request) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, request));
    }

    // Desactiva un usuario (soft delete) — solo SUPER_ADMIN y ADMIN_CENTRAL
    @PutMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    // Reactiva un usuario desactivado — solo SUPER_ADMIN y ADMIN_CENTRAL
    @PutMapping("/{id}/activar")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        usuarioService.activar(id);
        return ResponseEntity.noContent().build();
    }

    // Lista los maestros (usuarios con rol PROFESOR) — usado en "Asignar Materias a Maestros"
    @GetMapping("/profesores")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<UsuarioResponseDTO>> listarProfesores() {
        return ResponseEntity.ok(usuarioService.listarProfesores());
    }

    // Materias actualmente asignadas a un maestro
    @GetMapping("/{id}/materias")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<MateriaResponseDTO>> obtenerMaterias(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerMaterias(id));
    }

    // Reemplaza el conjunto de materias de un maestro por la lista enviada
    @PutMapping("/{id}/materias")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<MateriaResponseDTO>> asignarMaterias(
            @PathVariable Long id, @RequestBody @Valid AsignarMateriasRequestDTO request) {
        return ResponseEntity.ok(usuarioService.asignarMaterias(id, request.getMateriaIds()));
    }

    // Asignaciones (grupo, materia) del usuario logueado — usado en la reserva del profesor
    @GetMapping("/mis-asignaciones")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<AsignacionMaestroResponseDTO>> misAsignaciones() {
        return ResponseEntity.ok(usuarioService.obtenerMisAsignaciones());
    }

    // Asignaciones (grupo, materia) actuales de un maestro
    @GetMapping("/{id}/asignaciones")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<AsignacionMaestroResponseDTO>> obtenerAsignaciones(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerAsignaciones(id));
    }

    // Reemplaza las asignaciones (grupo, materia) del maestro — valida que la materia se imparta en el grupo
    @PutMapping("/{id}/asignaciones")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<AsignacionMaestroResponseDTO>> asignarGrupoMaterias(
            @PathVariable Long id, @RequestBody @Valid AsignarGrupoMateriasRequestDTO request) {
        return ResponseEntity.ok(usuarioService.asignarGrupoMaterias(id, request.getAsignaciones()));
    }

    // Devuelve el perfil del usuario actualmente logueado — cualquier usuario autenticado
    @GetMapping("/perfil")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<UsuarioResponseDTO> obtenerPerfil() {
        return ResponseEntity.ok(usuarioService.obtenerPerfil());
    }

    // Actualiza la foto de perfil del usuario logueado con una URL
    @PutMapping("/perfil")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<UsuarioResponseDTO> actualizarPerfil(@RequestBody @Valid UpdatePerfilRequestDTO request) {
        return ResponseEntity.ok(usuarioService.actualizarPerfil(request));
    }

    // Sube un archivo de imagen como foto de perfil del usuario logueado
    @PutMapping(value = "/perfil/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<UsuarioResponseDTO> subirFotoPerfil(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(usuarioService.subirFotoPerfil(file));
    }
}
