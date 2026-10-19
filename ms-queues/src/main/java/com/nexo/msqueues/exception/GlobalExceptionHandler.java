package com.nexo.msqueues.exception;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import software.amazon.awssdk.core.exception.SdkException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- 400: solicitudes inválidas ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400, "error", "Solicitud inválida", "detalle", errores));
    }

    /** Validaciones sobre parámetros de ruta o de consulta (por ejemplo, el nombre en /queues/{name}). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> validacionParametros(HandlerMethodValidationException e) {
        List<String> errores = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .map(String::valueOf)
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400, "error", "Solicitud inválida", "detalle", errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400, "error", "El cuerpo de la solicitud no es un JSON válido"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", e.getMessage()));
    }

    // --- 404: recurso inexistente ---

    @ExceptionHandler({QueueNotFoundException.class, TopicNotFoundException.class,
            SuscripcionNotFoundException.class})
    public ResponseEntity<Map<String, Object>> noExiste(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", 404, "error", e.getMessage()));
    }

    // --- 409: el recurso ya existe ---

    @ExceptionHandler({QueueAlreadyExistsException.class, TopicAlreadyExistsException.class,
            SuscripcionAlreadyExistsException.class})
    public ResponseEntity<Map<String, Object>> existe(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "error", e.getMessage()));
    }

    // --- 502: falla al hablar con AWS ---

    @ExceptionHandler(SdkException.class)
    public ResponseEntity<Map<String, Object>> aws(SdkException e) {
        log.error("Error de AWS: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("status", 502, "error", "Error al comunicarse con Amazon SQS/SNS"));
    }
}