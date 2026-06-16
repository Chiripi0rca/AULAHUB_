package com.aulahub.backend.exception;

/*
   Esta exception se lanzara si las credenciales del usuario estan incorrectas ya sea
   Email o Password incorrecto.
 */
public class CrendencialesInvalidasException extends RuntimeException {
    public CrendencialesInvalidasException(String message) {
        super(message);
    }
}
