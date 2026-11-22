package com.nexo.msproducts.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Una opción de compra de un producto: tamaño de pizza, porción de sushi, etc., con su precio y stock. */
@Entity
@Table(name = "variantes_producto", indexes = @Index(name = "idx_variante_producto", columnList = "producto_id"))
public class VarianteProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(nullable = false)
    private int orden = 0;

    public VarianteProducto() {
    }

    public UUID getId() { return id; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
}