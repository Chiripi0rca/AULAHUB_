package com.aulahub.backend.dto.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// DTO genérico para respuestas paginadas
// Envuelve el Page<T> de Spring y expone solo los campos que necesita el frontend
// Uso en controller: return ResponseEntity.ok(PageResponseDTO.from(page));
@Getter
public class PageResponseDTO<T> {

    private final List<T> contenido;       // Los elementos de la página actual
    private final int paginaActual;        // Número de página (empieza en 0)
    private final int totalPaginas;        // Total de páginas disponibles
    private final long totalElementos;     // Total de registros en toda la consulta
    private final int tamanioPagina;       // Elementos por página
    private final boolean primera;         // true si es la primera página
    private final boolean ultima;          // true si es la última página

    private PageResponseDTO(Page<T> page) {
        this.contenido = page.getContent();
        this.paginaActual = page.getNumber();
        this.totalPaginas = page.getTotalPages();
        this.totalElementos = page.getTotalElements();
        this.tamanioPagina = page.getSize();
        this.primera = page.isFirst();
        this.ultima = page.isLast();
    }

    // Método estático de fábrica — convierte un Page<T> de Spring a PageResponseDTO<T>
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return new PageResponseDTO<>(page);
    }
}
