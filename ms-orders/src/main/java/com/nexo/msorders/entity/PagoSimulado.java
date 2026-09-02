package com.nexo.msorders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagos_simulados")
public class PagoSimulado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String proveedor; // "SIMULADO" por ahora; "WEBPAY"/"MERCADOPAGO" a futuro

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(name = "monto_reembolsado")
    private BigDecimal montoReembolsado;

    private String metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(nullable = false)
    private Instant fecha;

    public PagoSimulado() {
    }

    @PrePersist
    protected void alCrear() {
        this.fecha = Instant.now();
        if (this.estado == null) {
            this.estado = Estado.PENDING;
        }
    }

    public UUID getId() { return id; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public BigDecimal getMontoReembolsado() { return montoReembolsado; }
    public void setMontoReembolsado(BigDecimal montoReembolsado) { this.montoReembolsado = montoReembolsado; }
    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getFecha() { return fecha; }

    public enum Estado {
        PENDING, APPROVED, REJECTED, REFUNDED
    }
}