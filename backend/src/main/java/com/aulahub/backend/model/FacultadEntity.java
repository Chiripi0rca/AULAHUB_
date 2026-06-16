package com.aulahub.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "facultades")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true — no pueden existir dos facultades con la misma clave
    @Column(nullable = false, unique = true, length = 20)
    private String clave;

    @Column(nullable = false)
    private String nombre;

    // @Builder.Default preserva true como valor por defecto al usar el builder
    // Cuando activa = false, sus aulas, materias y tipos_reserva dejan de estar disponibles
    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;
}
