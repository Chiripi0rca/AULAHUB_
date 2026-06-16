package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca un tipo de reserva por ID y no existe en la base de datos.
 */
public class TipoReservaNoEncontradoException extends RuntimeException {
    public TipoReservaNoEncontradoException(String message) {
        super(message);
    }
}
