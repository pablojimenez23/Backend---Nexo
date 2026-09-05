package com.nexo.msusers.repository;

import com.nexo.msusers.entity.CalificacionConductor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalificacionConductorRepository extends JpaRepository<CalificacionConductor, UUID> {

    List<CalificacionConductor> findByConductorId(UUID conductorId);

    boolean existsByPedidoId(UUID pedidoId);

}