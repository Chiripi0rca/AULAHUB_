package com.aulahub.backend.dto.response;

import com.aulahub.backend.model.enums.EstadoReserva;
import com.aulahub.backend.model.enums.Turno;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class ReservaResponseDTO {
    private Long id;
    private Long solicitanteId;
    private String solicitanteNombre;   // nombre del profesor que solicitó — para mostrar en la UI
    private Long aulaId;
    private String aulaNombre;
    private Long tipoReservaId;
    private String tipoReservaNombre;
    private String tipoReservaClave;
    private int tipoReservaPrioridad;
    private Long materiaId;
    private String materiaNombre;       // null cuando la reserva no tiene materia (evento/mantenimiento)
    private String grupo;
    private Turno turno;

    // Formato mexicano dd-MM-yyyy (ej: 05-06-2026)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
    private LocalDate fecha;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horaInicio;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horaFin;
    private boolean esExterno;
    private EstadoReserva status;
    private String motivoRechazo; // null cuando no fue rechazada, "Rechazada automáticamente por el sistema" si venció
    private LocalDateTime createdAt;
}
