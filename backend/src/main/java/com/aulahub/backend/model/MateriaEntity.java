package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "materias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MateriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facultad_id", nullable = false)
    private FacultadEntity facultad;

    @Column(nullable = false)
    private String nombre;

    // @Builder.Default preserva true como valor por defecto al usar el builder
    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;
}
