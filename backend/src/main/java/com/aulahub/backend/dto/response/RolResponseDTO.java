package com.aulahub.backend.dto.response;

import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.enums.Turno;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class RolResponseDTO {
    private Long id;
    private Long usuarioId;
    private String usuarioNombre;   // Para mostrar el nombre en la UI sin llamada extra
    private String usuarioEmail;
    private Long facultadId;
    private String facultadNombre;  // Para mostrar la facultad sin llamada extra
    private TipoRol tipo;
    private Turno turno;
    private List<AulaResponseDTO> aulas;
}
