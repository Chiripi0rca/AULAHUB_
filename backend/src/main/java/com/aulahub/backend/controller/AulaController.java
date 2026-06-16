package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.AulaRequestDTO;
import com.aulahub.backend.dto.response.AulaResponseDTO;
import com.aulahub.backend.service.AulaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final AulaService aulaService;

    // Si se pasa ?facultadId=X devuelve solo las aulas de esa facultad, si no devuelve todas las activas.
    // Por defecto el listado NO incluye la imagen base64 (payload ligero). Las pantallas que
    // muestran thumbnail por fila pasan ?conImagen=true para recibirla.
    @GetMapping
    public ResponseEntity<List<AulaResponseDTO>> listar(
            @RequestParam(required = false) Long facultadId,
            @RequestParam(defaultValue = "false") boolean conImagen) {
        List<AulaResponseDTO> aulas = (facultadId != null)
                ? aulaService.listarPorFacultad(facultadId, conImagen)
                : aulaService.listar(conImagen);
        return ResponseEntity.ok(aulas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> obtener(@PathVariable Long id) { // Endpoint para obtener una aula por su ID
        return ResponseEntity.ok(aulaService.obtenerPorId(id)); // Llamada al servicio para obtener una aula por su ID y retorno de la respuesta
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<AulaResponseDTO> crear(@RequestBody @Valid AulaRequestDTO request) { // Endpoint para crear una nueva aula
        return ResponseEntity.ok(aulaService.crear(request)); // Llamada al servicio para crear una nueva aula y retorno de la respuesta
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<AulaResponseDTO> actualizar(@PathVariable Long id, @RequestBody @Valid AulaRequestDTO request) { // Endpoint para actualizar una aula existente
        return ResponseEntity.ok(aulaService.actualizar(id, request)); // Llamada al servicio para actualizar una aula existente y retorno de la respuesta
    }

    // Sube o reemplaza la imagen del aula — se guarda como base64 en imagen_url (LONGTEXT)
    @PutMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<AulaResponseDTO> subirImagen(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(aulaService.subirImagen(id, file));
    }

    @PutMapping("/{id}/desactivar") // Soft delete — solo pone activa = false
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        aulaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activar") // Reactiva un aula desactivada — pone activa = true
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        aulaService.activar(id);
        return ResponseEntity.noContent().build();
    }
}
