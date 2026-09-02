package com.nexo.msorders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "tienda_id", nullable = false)
    private UUID tiendaId;

    @Column(name = "conductor_id")
    private UUID conductorId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "pago_id")
    private PagoSimulado pago;

    @Column(name = "direccion_envio", nullable = false)
    private String direccionEnvio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "costo_envio", nullable = false)
    private BigDecimal costoEnvio;

    @Column(name = "comision_plataforma", nullable = false)
    private BigDecimal comisionPlataforma;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "cargo_cancelacion")
    private BigDecimal cargoCancelacion;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public Pedido() {
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = Instant.now();
        if (this.estado == null) {
            this.estado = Estado.CREATED;
        }
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public void setClienteId(UUID clienteId) { this.clienteId = clienteId; }
    public UUID getTiendaId() { return tiendaId; }
    public void setTiendaId(UUID tiendaId) { this.tiendaId = tiendaId; }
    public UUID getConductorId() { return conductorId; }
    public void setConductorId(UUID conductorId) { this.conductorId = conductorId; }
    public PagoSimulado getPago() { return pago; }
    public void setPago(PagoSimulado pago) { this.pago = pago; }
    public String getDireccionEnvio() { return direccionEnvio; }
    public void setDireccionEnvio(String direccionEnvio) { this.direccionEnvio = direccionEnvio; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getCostoEnvio() { return costoEnvio; }
    public void setCostoEnvio(BigDecimal costoEnvio) { this.costoEnvio = costoEnvio; }
    public BigDecimal getComisionPlataforma() { return comisionPlataforma; }
    public void setComisionPlataforma(BigDecimal comisionPlataforma) { this.comisionPlataforma = comisionPlataforma; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getCargoCancelacion() { return cargoCancelacion; }
    public void setCargoCancelacion(BigDecimal cargoCancelacion) { this.cargoCancelacion = cargoCancelacion; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public Instant getCreadoEn() { return creadoEn; }

    public enum Estado {
        CREATED, PAID, CONFIRMED, PREPARING, READY, DELIVERING, DELIVERED, CANCELLED
    }
}