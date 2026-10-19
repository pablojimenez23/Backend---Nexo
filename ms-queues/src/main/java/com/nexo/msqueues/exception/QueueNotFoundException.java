package com.nexo.msqueues.exception;

public class QueueNotFoundException extends RuntimeException {
    public QueueNotFoundException(String name) {
        super("La cola '" + name + "' no existe");
    }
}