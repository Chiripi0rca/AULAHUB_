package com.aulahub.backend.model.enums;

// Un enum simple de dos valores que representa el turno del día
// Se usa en dos lugares del proyecto: RolEntity y ReservaEntity

public enum Turno {
    MATUTINO,
    VESPERTINO,
    AMBOS  // El ADMIN gestiona reservas de ambos turnos
}

/* MATUTINO — el ADMIN solo gestiona reservas del turno mañana
   En ReservaEntity indica que la reserva es en horario matutino */

/* VESPERTINO — el ADMIN solo gestiona reservas del turno tarde
   En ReservaEntity indica que la reserva es en horario vespertino */

/* AMBOS — el ADMIN gestiona reservas de los dos turnos (mañana y tarde)
   No aplica en ReservaEntity; una reserva siempre tiene un turno específico */