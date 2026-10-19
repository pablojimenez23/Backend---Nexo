package com.nexo.msqueues.exception;

public class QueueAlreadyExistsException extends RuntimeException {
    public QueueAlreadyExistsException(String name) {
        super("La cola '" + name + "' ya existe");
    }
}