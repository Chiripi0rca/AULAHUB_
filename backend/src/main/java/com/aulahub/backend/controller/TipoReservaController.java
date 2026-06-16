package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.TipoReservaRequestDTO;
import com.aulahub.backend.dto.response.TipoReservaResponseDTO;
import com.aulahub.backend.service.TipoReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-reserva")
@RequiredArgsConstructor
public class TipoReservaController {

    private final TipoReservaService tipoReservaService;

    @GetMapping
    public ResponseEntity<List<TipoReservaResponseDTO>> listar() { // Endpoint para listar los tipos de reserva
        return ResponseEntity.ok(tipoReservaService.listar()); // Llamada al servicio para obtener la lista de tipos de reserva y retorno de la respuesta
    }

    // Tipos de reserva que el usuario autenticado puede usar según su rol — alimenta el dropdown de reserva
    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<List<TipoReservaResponseDTO>> listarDisponibles() {
        return ResponseEntity.ok(tipoReservaService.listarDisponibles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoReservaResponseDTO> obtener(@PathVariable Long id) { // Endpoint para obtener un tipo de reserva por su ID
        return ResponseEntity.ok(tipoReservaService.obtenerPorId(id)); // Llamada al servicio para obtener un tipo de reserva por su ID y retorno de la respuesta
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<TipoReservaResponseDTO> crear(@RequestBody @Valid TipoReservaRequestDTO request) { // Endpoint para crear un nuevo tipo de reserva
        return ResponseEntity.ok(tipoReservaService.crear(request)); // Llamada al servicio para crear un nuevo tipo de reserva y retorno de la respuesta
    }

    @PutMapping("/{id}") // Endpoint para actualizar un tipo de reserva
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<TipoReservaResponseDTO> actualizar(@PathVariable Long id, @RequestBody @Valid TipoReservaRequestDTO request) {
        return ResponseEntity.ok(tipoReservaService.actualizar(id, request));
    }

    @PutMapping("/{id}/desactivar") // Soft delete — solo pone activo = false
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        tipoReservaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activar") // Reactiva un tipo de reserva desactivado — pone activo = true
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        tipoReservaService.activar(id);
        return ResponseEntity.noContent().build();
    }
}
