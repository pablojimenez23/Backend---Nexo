package com.nexo.msqueues.exception;

public class TopicAlreadyExistsException extends RuntimeException {
    public TopicAlreadyExistsException(String name) {
        super("El topic '" + name + "' ya existe");
    }
}