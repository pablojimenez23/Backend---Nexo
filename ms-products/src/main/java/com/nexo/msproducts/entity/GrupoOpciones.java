package com.nexo.msproducts.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Una elección que el cliente hace al comprar: "Masa" (obligatoria, elige 1), "Extras" (opcional, hasta 5).
 * Es obligatoria cuando el mínimo es 1 o más.
 */
@Entity
@Table(name = "grupos_opciones", indexes = @Index(name = "idx_grupo_producto", columnList = "producto_id"))
public class GrupoOpciones {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int minimo = 0;

    @Column(nullable = false)
    private int maximo = 1;

    @Column(nullable = false)
    private int orden = 0;

    public GrupoOpciones() {
    }

    public boolean isObligatorio() { return minimo >= 1; }

    public UUID getId() { return id; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getMinimo() { return minimo; }
    public void setMinimo(int minimo) { this.minimo = minimo; }
    public int getMaximo() { return maximo; }
    public void setMaximo(int maximo) { this.maximo = maximo; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
}