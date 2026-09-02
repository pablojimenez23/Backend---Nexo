package com.nexo.msstores.repository;

import com.nexo.msstores.entity.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalificacionRepository extends JpaRepository<Calificacion, UUID> {

    List<Calificacion> findByTiendaId(UUID tiendaId);

}