package com.nexo.msqueues.exception;

public class TopicNotFoundException extends RuntimeException {
    public TopicNotFoundException(String name) {
        super("El topic '" + name + "' no existe");
    }
}