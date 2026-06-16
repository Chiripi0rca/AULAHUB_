package com.aulahub.backend.model;

import com.aulahub.backend.model.enums.EstadoReserva;
import com.aulahub.backend.model.enums.Turno;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// RLS — filtra reservas por facultad (para SUPER_ADMIN)
// Se activa en el service con: session.enableFilter("filtroFacultadReserva").setParameter("facultadId", id)
@FilterDef(name = "filtroFacultadReserva",
        parameters = @ParamDef(name = "facultadId", type = Long.class))
@Filter(name = "filtroFacultadReserva",
        condition = "aula_id IN (SELECT id FROM aulas WHERE facultad_id = :facultadId)")
@Entity
@Table(name = "reservas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private UserEntity solicitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id", nullable = false)
    private AulaEntity aula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_reserva_id", nullable = false)
    private TipoReservaEntity tipoReserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_id")
    private MateriaEntity materia;

    @Column(length = 50)
    private String grupo;

    // MATUTINO o VESPERTINO — @Enumerated guarda el nombre en MySQL
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Turno turno;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // @Builder.Default preserva false como valor por defecto al usar el builder
    @Builder.Default
    @Column(name = "es_externo", nullable = false)
    private boolean esExterno = false;

    // @Builder.Default preserva PENDIENTE como estado inicial al usar el builder
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva status = EstadoReserva.PENDIENTE;

    // Motivo cuando la reserva fue rechazada — null si no fue rechazada
    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    // updatable = false — Hibernate nunca modifica este campo después del INSERT
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
