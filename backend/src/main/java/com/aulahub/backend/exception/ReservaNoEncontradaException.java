package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca una reserva por ID y no existe en la base de datos.
 */
public class ReservaNoEncontradaException extends RuntimeException {
    public ReservaNoEncontradaException(String message) {
        super(message);
    }
}
