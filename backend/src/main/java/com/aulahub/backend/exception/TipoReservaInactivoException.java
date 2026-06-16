package com.aulahub.backend.exception;

/*
   Se lanza cuando se intenta crear una reserva usando un tipo de reserva que tiene activo = false.
 */
public class TipoReservaInactivoException extends RuntimeException {
    public TipoReservaInactivoException(String message) {
        super(message);
    }
}
