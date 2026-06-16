package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca una facultad por ID o clave y no existe en la base de datos.
 */
public class FacultadNoEncontradaException extends RuntimeException {
    public FacultadNoEncontradaException(String message) {
        super(message);
    }
}
