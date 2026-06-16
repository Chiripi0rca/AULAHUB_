package com.aulahub.backend.exception;

/*
   Se lanza cuando el aula solicitada ya tiene una reserva PENDIENTE o ACEPTADA
   que se traslapa con el horario de la nueva reserva.
 */
public class ReservaConflictoException extends RuntimeException {
    public ReservaConflictoException(String message) {
        super(message);
    }
}
