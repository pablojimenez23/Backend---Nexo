package com.nexo.msproducts.entity;

/** Tipos de producto de delivery. etiquetaVariante es cómo se llama la elección de tamaño en cada tipo. */
public enum TipoProducto {
    PIZZA("Pizzas", "Tamaño"),
    HAMBURGUESA("Hamburguesas", "Versión"),
    SUSHI("Sushi", "Porción"),
    SANDWICH("Sándwiches", "Tamaño"),
    EMPANADA("Empanadas", "Cantidad"),
    POLLO("Pollo", "Porción"),
    PASTA("Pastas", "Tamaño"),
    PLATO("Platos", "Porción"),
    ENSALADA("Ensaladas", "Tamaño"),
    POSTRE("Postres", "Porción"),
    CAFE("Café y pastelería", "Tamaño"),
    BEBIDA("Bebidas", "Formato"),
    OTRO("Otros", "Opción");

    private final String etiqueta;
    private final String etiquetaVariante;

    TipoProducto(String etiqueta, String etiquetaVariante) {
        this.etiqueta = etiqueta;
        this.etiquetaVariante = etiquetaVariante;
    }

    public String getEtiqueta() { return etiqueta; }
    public String getEtiquetaVariante() { return etiquetaVariante; }
}