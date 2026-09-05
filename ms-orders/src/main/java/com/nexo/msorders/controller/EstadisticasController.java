package com.nexo.msorders.controller;

import com.nexo.msorders.entity.Pedido;
import com.nexo.msorders.repository.PedidoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pedidos/estadisticas")
public class EstadisticasController {

    private final PedidoRepository pedidoRepository;

    public EstadisticasController(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> obtenerEstadisticas() {
        List<Pedido> todos = pedidoRepository.findAll();
        Instant inicioDeHoy = Instant.now().truncatedTo(ChronoUnit.DAYS);

        long totalPedidos = todos.size();
        long pedidosHoy = todos.stream()
                .filter(p -> p.getCreadoEn() != null && !p.getCreadoEn().isBefore(inicioDeHoy))
                .count();
        long pedidosCompletados = todos.stream()
                .filter(p -> p.getEstado() == Pedido.Estado.COMPLETED)
                .count();
        long pedidosCancelados = todos.stream()
                .filter(p -> p.getEstado() == Pedido.Estado.CANCELLED)
                .count();
        long pedidosActivos = totalPedidos - pedidosCompletados - pedidosCancelados;

        BigDecimal ventasTotales = todos.stream()
                .filter(p -> p.getEstado() == Pedido.Estado.COMPLETED)
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal comisionTotal = todos.stream()
                .filter(p -> p.getEstado() == Pedido.Estado.COMPLETED)
                .map(Pedido::getComisionPlataforma)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalPedidos", totalPedidos,
                "pedidosHoy", pedidosHoy,
                "pedidosActivos", pedidosActivos,
                "pedidosCompletados", pedidosCompletados,
                "pedidosCancelados", pedidosCancelados,
                "ventasTotales", ventasTotales,
                "comisionTotal", comisionTotal
        );
    }
}