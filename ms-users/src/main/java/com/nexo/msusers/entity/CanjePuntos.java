package com.nexo.msusers.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Puntos gastados por un cliente al pagar productos con puntos. Se revierte si el pedido se cancela. */
@Entity
@Table(name = "canjes_puntos")
public class CanjePuntos {

    public enum Estado { ACTIVO, REVERTIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private long puntos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "revertido_en")
    private Instant revertidoEn;

    public CanjePuntos() {
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = Instant.now();
        if (this.estado == null) {
            this.estado = Estado.ACTIVO;
        }
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public long getPuntos() { return puntos; }
    public void setPuntos(long puntos) { this.puntos = puntos; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getRevertidoEn() { return revertidoEn; }
    public void setRevertidoEn(Instant revertidoEn) { this.revertidoEn = revertidoEn; }
}