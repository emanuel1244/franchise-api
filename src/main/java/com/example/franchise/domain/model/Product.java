package com.example.franchise.domain.model;

public record Product(String id, String name, int stock) {

    public Product {
        requireValidName(name);
        requireValidStock(stock);
    }

    public static String requireValidName(String name) {
        return Franchise.requireText(name, "El nombre del producto es obligatorio");
    }

    public static int requireValidStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        return stock;
    }
}
