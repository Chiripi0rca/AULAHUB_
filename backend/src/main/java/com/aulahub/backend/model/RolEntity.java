package com.aulahub.backend.model;

import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.enums.Turno;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facultad_id")
    private FacultadEntity facultad; // null para ADMIN_CENTRAL (scope global)

    // @Builder.Default preserva PROFESOR como rol por defecto al usar el builder
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoRol tipo = TipoRol.PROFESOR;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Turno turno; // Solo aplica para ADMIN — MATUTINO o VESPERTINO

    // Aulas asignadas a este rol (solo aplica para ADMIN)
    // SUPER_ADMIN gestiona todas las aulas de su facultad, no necesita lista aquí
    // @Builder.Default evita NullPointerException al usar el builder
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rol_aulas",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "aula_id")
    )
    private List<AulaEntity> aulas = new ArrayList<>();
}
