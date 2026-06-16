package com.aulahub.backend.exception;

/*
   Se lanza cuando un usuario intenta realizar una operación que su rol no permite.
   Ejemplo: SUPER_ADMIN intentando crear un ADMIN_CENTRAL.
 */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String message) {
        super(message);
    }
}
