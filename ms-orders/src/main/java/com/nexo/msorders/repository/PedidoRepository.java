package com.nexo.msorders.repository;

import com.nexo.msorders.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    List<Pedido> findByClienteId(UUID clienteId);

    List<Pedido> findByTiendaId(UUID tiendaId);

    List<Pedido> findByEstadoAndConductorIdIsNull(Pedido.Estado estado);

    Optional<Pedido> findByConductorIdAndEstado(UUID conductorId, Pedido.Estado estado);

    // Historial de entregas del conductor (entregadas o ya completadas)
    List<Pedido> findByConductorIdAndEstadoIn(UUID conductorId, List<Pedido.Estado> estados);

    // Pedidos completados de una tienda, para calcular estadísticas de ventas
    List<Pedido> findByTiendaIdAndEstado(UUID tiendaId, Pedido.Estado estado);

    @Modifying
    @Query("UPDATE Pedido p SET p.conductorId = :conductorId, p.estado = 'DELIVERING' " +
           "WHERE p.id = :id AND p.conductorId IS NULL AND p.estado = 'READY'")
    int asignarConductor(@Param("id") UUID id, @Param("conductorId") UUID conductorId);

}