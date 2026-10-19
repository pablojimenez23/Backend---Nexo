package com.nexo.msqueues.exception;

public class SuscripcionNotFoundException extends RuntimeException {
    public SuscripcionNotFoundException(String topic, String queue) {
        super("La cola '" + queue + "' no está suscrita al topic '" + topic + "'");
    }
}