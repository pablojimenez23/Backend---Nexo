package com.nexo.msusers.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "movimientos_puntos")
public class MovimientoPuntos {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private UUID pedidoId;

    @Column(nullable = false)
    private long puntos;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public MovimientoPuntos() {
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getPedidoId() { return pedidoId; }
    public void setPedidoId(UUID pedidoId) { this.pedidoId = pedidoId; }
    public long getPuntos() { return puntos; }
    public void setPuntos(long puntos) { this.puntos = puntos; }
    public Instant getCreadoEn() { return creadoEn; }
}