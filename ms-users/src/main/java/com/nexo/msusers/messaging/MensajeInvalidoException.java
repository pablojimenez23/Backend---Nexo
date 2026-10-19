package com.nexo.msusers.messaging;

/** Error definitivo: reintentar no sirve. El mensaje se manda a la DLQ de inmediato. */
public class MensajeInvalidoException extends RuntimeException {
    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }
}