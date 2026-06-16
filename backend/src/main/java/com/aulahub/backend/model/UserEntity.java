package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // LONGTEXT — modificado en V6 para soportar imágenes base64
    @Column(name = "foto_url", columnDefinition = "LONGTEXT")
    private String fotoUrl;

    // @Builder.Default mantiene el valor true cuando se construye con el builder
    // Sin esto, @Builder pondría false (default de boolean) ignorando el inicializador
    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Materias que imparte el maestro (muchos-a-muchos) — tabla puente usuario_materias (V6)
    // Solo es relevante para usuarios con rol PROFESOR
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuario_materias",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "materia_id")
    )
    private List<MateriaEntity> materias = new ArrayList<>();
}
