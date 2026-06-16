package com.aulahub.backend.controller;

import com.aulahub.backend.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    // Exporta las reservas de un aula en PDF.
    // Parámetros opcionales ?desde=dd-MM-yyyy&hasta=dd-MM-yyyy para filtrar por rango de fechas.
    // Sin parámetros exporta todo el historial.
    @GetMapping("/aula/{aulaId}/pdf")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN_CENTRAL')")
    public ResponseEntity<byte[]> exportarPdfPorAula(
            @PathVariable Long aulaId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate hasta) {
        byte[] pdf = reporteService.generarPdfPorAula(aulaId, desde, hasta);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reservas-aula-" + aulaId + ".pdf")
                .body(pdf);
    }

    // Exporta todas las reservas de un profesor en PDF.
    // Parámetros opcionales ?desde=dd-MM-yyyy&hasta=dd-MM-yyyy para filtrar por rango de fechas.
    @GetMapping("/profesor/{usuarioId}/pdf")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportarPdfPorProfesor(
            @PathVariable Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate hasta) {
        byte[] pdf = reporteService.generarPdfPorProfesor(usuarioId, desde, hasta);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reservas-profesor-" + usuarioId + ".pdf")
                .body(pdf);
    }

    // Exporta todas las reservas del sistema en Excel.
    // Parámetros opcionales ?desde=dd-MM-yyyy&hasta=dd-MM-yyyy para filtrar por rango de fechas.
    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('ADMIN_CENTRAL')")
    public ResponseEntity<byte[]> exportarExcelTodasLasReservas(
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate hasta) {
        byte[] excel = reporteService.generarExcelTodasLasReservas(desde, hasta);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reporte-reservas.xlsx")
                .body(excel);
    }
}
