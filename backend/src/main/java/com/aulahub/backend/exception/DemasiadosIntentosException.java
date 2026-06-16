package com.aulahub.backend.exception;

/*
   Se lanza cuando una IP supera el número máximo de intentos de login fallidos
   dentro de la ventana de tiempo. Mapea a HTTP 429 (Too Many Requests).
 */
public class DemasiadosIntentosException extends RuntimeException {
    public DemasiadosIntentosException(String message) {
        super(message);
    }
}
