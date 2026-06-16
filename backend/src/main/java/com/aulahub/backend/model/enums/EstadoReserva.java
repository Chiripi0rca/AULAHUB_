package com.aulahub.backend.model.enums;

public enum EstadoReserva {
    PENDIENTE,
    ACEPTADA,
    RECHAZADA,
    CANCELADA
}
// el enum garantiza que solo puedas usar valores válidos y predefinidos

/* Estado inicial cuando un PROFESOR crea una reserva, La reserva espera que un ADMIN la revise
-PENDIENTE- */

/* El ADMIN aprobó la reserva, El aula queda bloqueada para esa fecha y horario
-ACEPTADA- */

/* El ADMIN rechazó la reserva, El aula queda libre nuevamente
-RECHAZADA- */

/* El PROFESOR canceló su propia reserva, Puede cancelar si está en estado PENDIENTE o ACEPTADA
-CANCELADA- */
