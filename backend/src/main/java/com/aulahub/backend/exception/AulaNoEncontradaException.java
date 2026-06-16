package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca un aula por ID y no existe en la base de datos.
 */
public class AulaNoEncontradaException extends RuntimeException {
    public AulaNoEncontradaException(String message) {
        super(message);
    }
}
