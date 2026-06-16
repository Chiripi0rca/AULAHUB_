package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    // @Builder.Default preserva false como valor por defecto al usar el builder
    // Cuando el usuario abre la notificación se actualiza a true
    @Builder.Default
    @Column(nullable = false)
    private boolean leido = false;

    @Column(length = 50)
    private String tipo; // RESERVA_ACEPTADA | RESERVA_RECHAZADA | RESERVA_CANCELADA | SISTEMA

    // @Builder.Default preserva LocalDateTime.now() como valor por defecto al usar el builder
    // updatable = false — Hibernate nunca modifica este campo después del INSERT
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
