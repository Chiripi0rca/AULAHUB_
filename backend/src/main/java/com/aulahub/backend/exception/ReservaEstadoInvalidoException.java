package com.aulahub.backend.exception;

/*
   Se lanza cuando se intenta hacer una transicion de estado invalida en una reserva.
   Ejemplos: aprobar una reserva ya RECHAZADA, o cancelar una ya CANCELADA.
 */
public class ReservaEstadoInvalidoException extends RuntimeException {
    public ReservaEstadoInvalidoException(String message) {
        super(message);
    }
}
