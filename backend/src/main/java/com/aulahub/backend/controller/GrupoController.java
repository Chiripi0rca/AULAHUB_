package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.AsignarMateriasRequestDTO;
import com.aulahub.backend.dto.request.GrupoRequestDTO;
import com.aulahub.backend.dto.response.GrupoResponseDTO;
import com.aulahub.backend.dto.response.MateriaResponseDTO;
import com.aulahub.backend.service.GrupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupoService;

    // Lista los grupos — si se pasa ?facultadId=X filtra por facultad (solo activos),
    // si no se pasa devuelve todos. Alimenta dropdowns en el frontend.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<GrupoResponseDTO>> listar(
            @RequestParam(required = false) Long facultadId) {
        List<GrupoResponseDTO> grupos = (facultadId != null)
                ? grupoService.listarPorFacultad(facultadId)
                : grupoService.listar();
        return ResponseEntity.ok(grupos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<GrupoResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(grupoService.obtenerPorId(id));
    }

    // Materias que se imparten en el grupo — usado para filtrar al asignar al maestro
    @GetMapping("/{id}/materias")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<MateriaResponseDTO>> materias(@PathVariable Long id) {
        return ResponseEntity.ok(grupoService.listarMaterias(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<GrupoResponseDTO> crear(@RequestBody @Valid GrupoRequestDTO request) {
        return ResponseEntity.ok(grupoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<GrupoResponseDTO> actualizar(@PathVariable Long id,
                                                       @RequestBody @Valid GrupoRequestDTO request) {
        return ResponseEntity.ok(grupoService.actualizar(id, request));
    }

    // Reemplaza las materias que se imparten en el grupo
    @PutMapping("/{id}/materias")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<MateriaResponseDTO>> asignarMaterias(
            @PathVariable Long id, @RequestBody @Valid AsignarMateriasRequestDTO request) {
        return ResponseEntity.ok(grupoService.asignarMaterias(id, request.getMateriaIds()));
    }

    @PutMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        grupoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activar")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        grupoService.activar(id);
        return ResponseEntity.noContent().build();
    }
}
