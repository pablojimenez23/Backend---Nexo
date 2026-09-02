package com.nexo.msorders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMITE_POR_MINUTO = 60;
    private final ConcurrentHashMap<String, Ventana> contadores = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        long ahora = System.currentTimeMillis();
        Ventana ventana = contadores.computeIfAbsent(ip, k -> new Ventana(ahora));

        synchronized (ventana) {
            if (ahora - ventana.inicio > 60_000) {
                ventana.inicio = ahora;
                ventana.contador.set(0);
            }
            if (ventana.contador.incrementAndGet() > LIMITE_POR_MINUTO) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Demasiadas solicitudes, intenta más tarde\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static class Ventana {
        volatile long inicio;
        final AtomicInteger contador = new AtomicInteger(0);
        Ventana(long inicio) { this.inicio = inicio; }
    }
}