package com.aulahub.backend.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CrendencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> handleCredencialesInvalidas(
            CrendencialesInvalidasException ex, WebRequest request) {
        return build(401, "Unauthorized", ex.getMessage(), request, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleUsuarioNoEncontrado(
            UsuarioNoEncontradoException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(AulaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleAulaNoEncontrada(
            AulaNoEncontradaException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(ReservaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleReservaNoEncontrada(
            ReservaNoEncontradaException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(FacultadNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleFacultadNoEncontrada(
            FacultadNoEncontradaException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(MateriaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleMateriaNoEncontrada(
            MateriaNoEncontradaException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(TipoReservaNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleTipoReservaNoEncontrado(
            TipoReservaNoEncontradoException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(RolNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRolNoEncontrado(
            RolNoEncontradoException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(GrupoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleGrupoNoEncontrado(
            GrupoNoEncontradoException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(EmailYaRegistradoException.class)
    public ResponseEntity<ErrorResponse> handleEmailYaRegistrado(
            EmailYaRegistradoException ex, WebRequest request) {
        return build(409, "Conflict", ex.getMessage(), request, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ReservaConflictoException.class)
    public ResponseEntity<ErrorResponse> handleReservaConflicto(
            ReservaConflictoException ex, WebRequest request) {
        return build(409, "Conflict", ex.getMessage(), request, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ReservaEstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleReservaEstadoInvalido(
            ReservaEstadoInvalidoException ex, WebRequest request) {
        return build(422, "Unprocessable Entity", ex.getMessage(), request, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> handleOperacionNoPermitida(
            OperacionNoPermitidaException ex, WebRequest request) {
        return build(422, "Unprocessable Entity", ex.getMessage(), request, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(AulaInactivaException.class)
    public ResponseEntity<ErrorResponse> handleAulaInactiva(
            AulaInactivaException ex, WebRequest request) {
        return build(422, "Unprocessable Entity", ex.getMessage(), request, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(TipoReservaInactivoException.class)
    public ResponseEntity<ErrorResponse> handleTipoReservaInactivo(
            TipoReservaInactivoException ex, WebRequest request) {
        return build(422, "Unprocessable Entity", ex.getMessage(), request, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ErrorResponse> handleDemasiadosIntentos(
            DemasiadosIntentosException ex, WebRequest request) {
        return build(429, "Too Many Requests", ex.getMessage(), request, HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<ErrorResponse> notFound(String message, WebRequest request) {
        return build(404, "Not Found", message, request, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<ErrorResponse> build(int status, String error, String message,
                                                  WebRequest request, HttpStatus httpStatus) {
        return new ResponseEntity<>(
                new ErrorResponse(LocalDateTime.now(), status, error, message, request.getDescription(false)),
                httpStatus
        );
    }
}
