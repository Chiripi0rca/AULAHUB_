package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;

// Qué materia imparte un maestro en qué grupo.
// La combinación (usuario, grupo, materia) es única; y (grupo, materia) debe existir
// en grupo_materias (validado en el service al asignar).
@Entity
@Table(
        name = "asignaciones_maestro",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_asignaciones_maestro",
                columnNames = {"usuario_id", "grupo_id", "materia_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionMaestroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoEntity grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_id", nullable = false)
    private MateriaEntity materia;
}
