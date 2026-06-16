package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca un grupo por ID y no existe en la base de datos.
 */
public class GrupoNoEncontradoException extends RuntimeException {
    public GrupoNoEncontradoException(String message) {
        super(message);
    }
}
