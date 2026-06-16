package com.aulahub.backend.dto.response;

import com.aulahub.backend.model.enums.Turno;
import lombok.AllArgsConstructor;
import lombok.Getter;

// Una asignación (grupo, materia) de un maestro, con nombres para mostrar en la UI.
@Getter
@AllArgsConstructor
public class AsignacionMaestroResponseDTO {
    private Long id;
    private Long grupoId;
    private String grupoNombre;
    private Turno grupoTurno; // turno del grupo — el front autorrellena y bloquea el turno al reservar
    private Long materiaId;
    private String materiaNombre;
}
