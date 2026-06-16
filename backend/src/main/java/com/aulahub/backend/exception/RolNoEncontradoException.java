package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca un rol por ID y no existe en la base de datos.
 */
public class RolNoEncontradoException extends RuntimeException {
    public RolNoEncontradoException(String message) {
        super(message);
    }
}
