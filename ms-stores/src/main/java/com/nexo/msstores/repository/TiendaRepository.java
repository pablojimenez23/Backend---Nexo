package com.nexo.msstores.repository;

import com.nexo.msstores.entity.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TiendaRepository extends JpaRepository<Tienda, UUID> {

    List<Tienda> findByEstado(Tienda.Estado estado);

    List<Tienda> findByOwnerId(UUID ownerId);

    List<Tienda> findByEstadoAndCategoria(Tienda.Estado estado, Tienda.CategoriaTienda categoria);

}