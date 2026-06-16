package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.MateriaRequestDTO;
import com.aulahub.backend.dto.response.MateriaResponseDTO;
import com.aulahub.backend.service.MateriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materias") // Ruta base para el controlador de materias
@RequiredArgsConstructor
public class MateriaController {

    private final MateriaService materiaService;

    // Lista materias. Si se pasa ?facultadId=X filtra por esa facultad.
    // Para SUPER_ADMIN aplica RLS automático por su facultad aunque no se pase el parámetro.
    @GetMapping
    public ResponseEntity<List<MateriaResponseDTO>> listar(
            @RequestParam(required = false) Long facultadId) {
        return ResponseEntity.ok(
            facultadId != null ? materiaService.listarPorFacultad(facultadId) : materiaService.listar()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<MateriaResponseDTO> obtener(@PathVariable Long id) { // Endpoint para obtener una materia por su ID
        return ResponseEntity.ok(materiaService.obtenerPorId(id)); // Llamada al servicio para obtener una materia por su ID y retorno de la respuesta
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<MateriaResponseDTO> crear(@RequestBody @Valid MateriaRequestDTO request) { // Endpoint para crear una nueva materia
        return ResponseEntity.ok(materiaService.crear(request)); // Llamada al servicio para crear una nueva materia y retorno de la respuesta
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<MateriaResponseDTO> actualizar(@PathVariable Long id, @RequestBody @Valid MateriaRequestDTO request) { // Endpoint para actualizar una materia existente
        return ResponseEntity.ok(materiaService.actualizar(id, request)); // Llamada al servicio para actualizar una materia existente y retorno de la respuesta
    }

    @PutMapping("/{id}/desactivar") // Soft delete — solo pone activa = false
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        materiaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activar") // Reactiva una materia desactivada — pone activa = true
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        materiaService.activar(id);
        return ResponseEntity.noContent().build();
    }
}
