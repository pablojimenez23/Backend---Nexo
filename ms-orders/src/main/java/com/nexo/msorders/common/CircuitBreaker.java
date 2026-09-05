package com.nexo.msorders.common;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker simple, sin dependencias externas.
 * Estados: CERRADO (funciona normal) -> ABIERTO (bloquea llamadas tras varios fallos)
 * -> SEMIABIERTO (deja pasar una prueba tras el tiempo de espera).
 */
public class CircuitBreaker {

    private final int umbralFallos;
    private final long tiempoEsperaMs;

    private final AtomicInteger fallosConsecutivos = new AtomicInteger(0);
    private final AtomicLong ultimoFallo = new AtomicLong(0);
    private volatile boolean abierto = false;

    public CircuitBreaker(int umbralFallos, long tiempoEsperaMs) {
        this.umbralFallos = umbralFallos;
        this.tiempoEsperaMs = tiempoEsperaMs;
    }

    public boolean permiteLlamada() {
        if (!abierto) {
            return true;
        }
        // Pasado el tiempo de espera, dejamos pasar una llamada de prueba (semiabierto)
        return System.currentTimeMillis() - ultimoFallo.get() > tiempoEsperaMs;
    }

    public void registrarExito() {
        fallosConsecutivos.set(0);
        abierto = false;
    }

    public void registrarFallo() {
        ultimoFallo.set(System.currentTimeMillis());
        int fallos = fallosConsecutivos.incrementAndGet();
        if (fallos >= umbralFallos) {
            abierto = true;
        }
    }

    public boolean estaAbierto() {
        return abierto;
    }
}