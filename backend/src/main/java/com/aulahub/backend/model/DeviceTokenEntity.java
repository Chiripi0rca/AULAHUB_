package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Token de un dispositivo (telefono) registrado por un usuario para recibir push (FCM).
// Un usuario puede tener varios dispositivos. El token es unico.
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity usuario;

    @Column(nullable = false, length = 255, unique = true)
    private String token;

    @Column(length = 20)
    private String plataforma; // android | ios | web

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
