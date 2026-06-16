package com.aulahub.backend.model;

import com.aulahub.backend.model.enums.TipoRol;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "tipos_reserva",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tipos_reserva_fac_clave",
                columnNames = {"facultad_id", "clave"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoReservaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facultad_id", nullable = false)
    private FacultadEntity facultad;

    // Código del tipo — ej: "CLASE", "EXAMEN", "TUTORIA", "EVENTO", "MANTENIMIENTO"
    // Se mapea como String (no enum) porque cada facultad puede definir sus propios tipos
    @Column(nullable = false, length = 50)
    private String clave;

    @Column(nullable = false, length = 100)
    private String nombre;

    // @Builder.Default preserva true como valor por defecto al usar el builder
    // Cuando activo = false ya no se puede usar para crear nuevas reservas
    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    // Rol mínimo requerido para usar este tipo de reserva (jerárquico).
    // PROFESOR = lo puede usar cualquiera; ADMIN = solo admin y superiores; etc.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "rol_minimo", nullable = false, length = 20)
    private TipoRol rolMinimo = TipoRol.PROFESOR;

    // Prioridad para ordenar solicitudes que compiten por un horario (mayor = primero).
    @Builder.Default
    @Column(nullable = false)
    private int prioridad = 0;
}
