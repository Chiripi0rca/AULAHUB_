package com.aulahub.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Body de PUT /api/usuarios/{id}/materias
// Reemplaza por completo el conjunto de materias del maestro por esta lista (puede ir vacía)
@Getter
@Setter
@NoArgsConstructor
public class AsignarMateriasRequestDTO {

    @NotNull(message = "La lista de materias es obligatoria (puede ir vacía)")
    private List<Long> materiaIds = new ArrayList<>();
}
