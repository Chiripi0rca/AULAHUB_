package com.aulahub.backend.exception;

/*
   Se lanza cuando se intenta crear una reserva en un aula que tiene activa = false.
 */
public class AulaInactivaException extends RuntimeException {
    public AulaInactivaException(String message) {
        super(message);
    }
}
