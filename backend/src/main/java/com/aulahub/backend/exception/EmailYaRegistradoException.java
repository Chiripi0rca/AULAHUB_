package com.aulahub.backend.exception;

/*
   Se lanza cuando se intenta registrar un usuario con un email
   que ya existe en la base de datos.
 */
public class EmailYaRegistradoException extends RuntimeException {
    public EmailYaRegistradoException(String message) {
        super(message);
    }
}
