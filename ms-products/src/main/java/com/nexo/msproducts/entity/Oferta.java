package com.nexo.msproducts.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ofertas")
public class Oferta {

    public enum Estado { ACTIVA, CANCELADA }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "tienda_id", nullable = false)
    private UUID tiendaId;

    @Column(nullable = false)
    private int porcentaje;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    /** Unidades máximas a precio de oferta; null = sin cupo. */
    private Integer cupo;

    @Column(name = "unidades_vendidas", nullable = false)
    private int unidadesVendidas = 0;

    @Column(name = "limite_por_pedido", nullable = false)
    private int limitePorPedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    public Oferta() {
    }

    /** Precio con el descuento aplicado, redondeado a pesos enteros. */
    public BigDecimal precioSobre(BigDecimal precio) {
        return precio.multiply(BigDecimal.valueOf(100L - porcentaje))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    public boolean tieneCupo() {
        return cupo == null || unidadesVendidas < cupo;
    }

    public UUID getId() { return id; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public UUID getTiendaId() { return tiendaId; }
    public void setTiendaId(UUID tiendaId) { this.tiendaId = tiendaId; }
    public int getPorcentaje() { return porcentaje; }
    public void setPorcentaje(int porcentaje) { this.porcentaje = porcentaje; }
    public Instant getInicio() { return inicio; }
    public void setInicio(Instant inicio) { this.inicio = inicio; }
    public Instant getFin() { return fin; }
    public void setFin(Instant fin) { this.fin = fin; }
    public Integer getCupo() { return cupo; }
    public void setCupo(Integer cupo) { this.cupo = cupo; }
    public int getUnidadesVendidas() { return unidadesVendidas; }
    public void setUnidadesVendidas(int unidadesVendidas) { this.unidadesVendidas = unidadesVendidas; }
    public int getLimitePorPedido() { return limitePorPedido; }
    public void setLimitePorPedido(int limitePorPedido) { this.limitePorPedido = limitePorPedido; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
}