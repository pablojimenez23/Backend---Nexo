package com.nexo.msusers.repository;

import com.nexo.msusers.entity.Conductor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConductorRepository extends JpaRepository<Conductor, UUID> {

    Optional<Conductor> findByUsuarioId(UUID usuarioId);

    List<Conductor> findByEstado(Conductor.Estado estado);

}