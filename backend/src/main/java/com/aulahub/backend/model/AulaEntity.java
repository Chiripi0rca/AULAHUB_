package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

// RLS — filtra aulas por facultad (para SUPER_ADMIN)
// Se activa en el service con: session.enableFilter("filtroFacultadAula").setParameter("facultadId", id)
@FilterDef(name = "filtroFacultadAula",
        parameters = @ParamDef(name = "facultadId", type = Long.class))
@Filter(name = "filtroFacultadAula",
        condition = "facultad_id = :facultadId")
@Entity
@Table(
        name = "aulas",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_aulas_facultad_codigo",
                columnNames = {"facultad_id", "codigo"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AulaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facultad_id", nullable = false)
    private FacultadEntity facultad;

    // Código corto del aula, ej: "labA", "labB", "auditorio"
    @Column(nullable = false, length = 50)
    private String codigo;

    // Nombre descriptivo del aula, ej: "Laboratorio de Redes"
    @Column(nullable = false)
    private String nombre;

    // @Builder.Default preserva true como valor por defecto al usar el builder
    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;

    // Capacidad máxima del aula — agregado en V6, nullable
    @Column
    private Integer capacidad;

    // URL de imagen del aula — LONGTEXT en BD (V6)
    @Column(name = "imagen_url", columnDefinition = "LONGTEXT")
    private String imagenUrl;
}
