package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

// RLS — filtra logs por facultad (para SUPER_ADMIN)
// Filtra por los usuarios que tienen rol en esa facultad
// Se activa en el service con: session.enableFilter("filtroFacultadLog").setParameter("facultadId", id)
@FilterDef(name = "filtroFacultadLog",
        parameters = @ParamDef(name = "facultadId", type = Long.class))
@Filter(name = "filtroFacultadLog",
        condition = "usuario_id IN (SELECT usuario_id FROM roles WHERE facultad_id = :facultadId)")
@Entity
@Table(name = "actividad_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActividadLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @Column(nullable = false, length = 100)
    private String accion;

    @Column(length = 50)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId; // Long plano — puede apuntar a cualquier tabla

    @Column(columnDefinition = "TEXT")
    private String detalle;

    // @Builder.Default preserva LocalDateTime.now() como valor por defecto al usar el builder
    // updatable = false — Hibernate nunca modifica este campo después del INSERT
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
