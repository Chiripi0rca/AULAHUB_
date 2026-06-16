package com.aulahub.backend.dto.request;

import com.aulahub.backend.model.enums.Turno;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
public class ReservaRequestDTO {

    @NotNull(message = "El aula es obligatoria")
    @Positive(message = "El ID del aula debe ser un número válido")
    private Long aulaId;

    @NotNull(message = "El tipo de reserva es obligatorio")
    @Positive(message = "El ID del tipo de reserva debe ser un número válido")
    private Long tipoReservaId;

    // Opcional — no todas las reservas tienen materia (ej: EVENTO, MANTENIMIENTO)
    @Positive(message = "El ID de la materia debe ser un número válido")
    private Long materiaId;

    // Opcional — grupo al que pertenece la clase (ej: "A", "B", "sistemas-3A")
    @Size(max = 50, message = "El grupo no puede exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "El grupo solo puede contener letras, números y guiones")
    private String grupo;

    @NotNull(message = "El turno es obligatorio")
    private Turno turno; // MATUTINO o VESPERTINO — validado por el enum automáticamente

    // Formato mexicano dd-MM-yyyy (ej: 05-06-2026)
    @NotNull(message = "La fecha de la reserva es obligatoria")
    @FutureOrPresent(message = "La fecha de la reserva no puede estar en el pasado")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
    private LocalDate fecha;

    @NotNull(message = "La hora de inicio es obligatoria")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horaFin;

    private Boolean esExterno = false;
}
