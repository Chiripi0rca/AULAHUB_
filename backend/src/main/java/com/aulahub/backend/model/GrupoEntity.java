package com.aulahub.backend.model;

import com.aulahub.backend.model.enums.Turno;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "grupos",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_grupos_fac_nombre",
                columnNames = {"facultad_id", "nombre"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrupoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facultad_id", nullable = false)
    private FacultadEntity facultad;

    // Nombre del grupo dentro de la facultad, ej: "4-1"
    @Column(nullable = false, length = 50)
    private String nombre;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    // Turno en el que opera el grupo. Al reservar, autorrellena y bloquea el turno
    // (un grupo vespertino no puede apartar horario matutino).
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Turno turno = Turno.MATUTINO;

    // Materias que se imparten en este grupo (muchos-a-muchos) — tabla puente grupo_materias.
    // Es la base para validar la asignación de un maestro a (grupo, materia).
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "grupo_materias",
            joinColumns = @JoinColumn(name = "grupo_id"),
            inverseJoinColumns = @JoinColumn(name = "materia_id")
    )
    private List<MateriaEntity> materias = new ArrayList<>();
}
