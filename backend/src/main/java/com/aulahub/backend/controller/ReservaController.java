package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.MotivoRequestDTO;
import com.aulahub.backend.dto.request.ReservaRequestDTO;
import com.aulahub.backend.dto.response.PageResponseDTO;
import com.aulahub.backend.dto.response.ReservaResponseDTO;
import com.aulahub.backend.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    // Lista todas las reservas paginadas
    // Ejemplo: GET /api/reservas?page=0&size=20&sort=fecha,desc
    @GetMapping
    public ResponseEntity<PageResponseDTO<ReservaResponseDTO>> listar(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(reservaService.listar(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }

    // Lista todas las reservas de un usuario específico paginadas
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<PageResponseDTO<ReservaResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(reservaService.listarPorUsuario(usuarioId, pageable)));
    }

    // Lista todas las reservas de un aula específica paginadas
    @GetMapping("/aula/{aulaId}")
    public ResponseEntity<PageResponseDTO<ReservaResponseDTO>> listarPorAula(
            @PathVariable Long aulaId,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(reservaService.listarPorAula(aulaId, pageable)));
    }

    // Reservas activas (PENDIENTE o ACEPTADA) de un aula en un rango de fechas
    // Alimenta la cuadrícula semanal del calendario para marcar los slots ocupados
    // Ejemplo: GET /api/reservas/aula/1/calendario?desde=01-06-2026&hasta=07-06-2026
    @GetMapping("/aula/{aulaId}/calendario")
    public ResponseEntity<List<ReservaResponseDTO>> calendario(
            @PathVariable Long aulaId,
            @RequestParam @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate desde,
            @RequestParam @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate hasta) {
        return ResponseEntity.ok(reservaService.listarCalendario(aulaId, desde, hasta));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<ReservaResponseDTO> crear(@RequestBody @Valid ReservaRequestDTO request) {
        return ResponseEntity.ok(reservaService.crear(request));
    }

    @PutMapping("/{id}/aceptar")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<ReservaResponseDTO> aceptar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.aceptar(id));
    }

    @PutMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<ReservaResponseDTO> rechazar(@PathVariable Long id,
                                                       @RequestBody @Valid MotivoRequestDTO requestDTO) {
        return ResponseEntity.ok(reservaService.rechazar(id, requestDTO.getMotivo()));
    }

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyAuthority('PROFESOR', 'ADMIN', 'SUPER_ADMIN', 'ADMIN_CENTRAL')")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(id));
    }
}
