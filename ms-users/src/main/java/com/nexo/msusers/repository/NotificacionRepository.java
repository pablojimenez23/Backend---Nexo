package com.nexo.msusers.repository;

import com.nexo.msusers.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    List<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);

    long countByUsuarioIdAndLeidaFalse(UUID usuarioId);

}