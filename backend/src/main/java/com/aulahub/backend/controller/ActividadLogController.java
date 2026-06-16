package com.aulahub.backend.controller;

import com.aulahub.backend.dto.response.ActividadLogResponseDTO;
import com.aulahub.backend.dto.response.PageResponseDTO;
import com.aulahub.backend.service.ActividadLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/actividad-log")
@RequiredArgsConstructor
public class ActividadLogController {

    private final ActividadLogService actividadLogService;

    // Logs del ADMIN paginados — filtrado por sus aulas asignadas y su turno.
    // Filtros opcionales: q (texto), accion, entidad, desde/hasta (fecha ISO yyyy-MM-dd).
    @GetMapping("/admin/{usuarioId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PageResponseDTO<ActividadLogResponseDTO>> listarParaAdmin(
            @PathVariable Long usuarioId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(
                actividadLogService.listarParaAdmin(usuarioId, q, accion, entidad, desde, hasta, pageable)));
    }

    // Logs del SUPER_ADMIN paginados — toda la actividad dentro de su facultad.
    // Filtros opcionales: q (texto), accion, entidad, desde/hasta (fecha ISO yyyy-MM-dd).
    @GetMapping("/super-admin/{usuarioId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<PageResponseDTO<ActividadLogResponseDTO>> listarParaSuperAdmin(
            @PathVariable Long usuarioId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(
                actividadLogService.listarParaSuperAdmin(usuarioId, q, accion, entidad, desde, hasta, pageable)));
    }

    // Logs del ADMIN_CENTRAL paginados — toda la actividad del sistema.
    // Filtros opcionales: q (texto), accion, entidad, desde/hasta (fecha ISO yyyy-MM-dd).
    @GetMapping("/admin-central")
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<PageResponseDTO<ActividadLogResponseDTO>> listarParaAdminCentral(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(
                actividadLogService.listarParaAdminCentral(q, accion, entidad, desde, hasta, pageable)));
    }
}
