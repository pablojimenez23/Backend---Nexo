package com.nexo.msqueues.exception;

public class SuscripcionAlreadyExistsException extends RuntimeException {
    public SuscripcionAlreadyExistsException(String topic, String queue) {
        super("La cola '" + queue + "' ya está suscrita al topic '" + topic + "'");
    }
}