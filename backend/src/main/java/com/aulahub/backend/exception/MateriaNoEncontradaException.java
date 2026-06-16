package com.aulahub.backend.exception;

/*
   Se lanza cuando se busca una materia por ID y no existe en la base de datos.
 */
public class MateriaNoEncontradaException extends RuntimeException {
    public MateriaNoEncontradaException(String message) {
        super(message);
    }
}
