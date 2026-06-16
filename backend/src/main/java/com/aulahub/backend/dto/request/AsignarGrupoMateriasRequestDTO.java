package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Body de PUT /api/usuarios/{id}/asignaciones
// Reemplaza por completo las asignaciones (grupo, materia) del maestro.
@Getter
@Setter
@NoArgsConstructor
public class AsignarGrupoMateriasRequestDTO {

    @NotNull(message = "La lista de asignaciones es obligatoria (puede ir vacía)")
    private List<Item> asignaciones = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @NotNull(message = "El grupo es obligatorio")
        private Long grupoId;

        @NotNull(message = "La materia es obligatoria")
        private Long materiaId;
    }
}
