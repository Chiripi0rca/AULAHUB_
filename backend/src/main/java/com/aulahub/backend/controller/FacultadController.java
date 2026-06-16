package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.FacultadRequestDTO;
import com.aulahub.backend.dto.response.FacultadResponseDTO;
import com.aulahub.backend.service.FacultadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facultades") // Ruta base para el controlador de facultades
@RequiredArgsConstructor
public class FacultadController {

    private final FacultadService facultadService;

    @GetMapping
    public ResponseEntity<List<FacultadResponseDTO>> listar() { // Endpoint para listar las facultades
        return ResponseEntity.ok(facultadService.listar()); // Llamada al servicio para obtener la lista de facultades y retorno de la respuesta
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacultadResponseDTO> obtener(@PathVariable Long id) { // Endpoint para obtener una facultad por su ID
        return ResponseEntity.ok(facultadService.obtenerPorId(id)); // Llamada al servicio para obtener una facultad por su ID y retorno de la respuesta
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<FacultadResponseDTO> crear(@RequestBody @Valid FacultadRequestDTO request) { // Endpoint para crear una nueva facultad
        return ResponseEntity.ok(facultadService.crear(request)); // Llamada al servicio para crear una nueva facultad y retorno de la respuesta
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<FacultadResponseDTO> actualizar(@PathVariable Long id,
                                                          @RequestBody @Valid FacultadRequestDTO request) {
        return ResponseEntity.ok(facultadService.actualizar(id, request));
    }

    @PutMapping("/{id}/desactivar") // Soft delete — desactiva la facultad y todo lo que tiene
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        facultadService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activar") // Reactiva una facultad desactivada
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        facultadService.activar(id);
        return ResponseEntity.noContent().build();
    }
}
