package com.example.franchise.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Branch(String id, String name, List<Product> products) {

    public Branch {
        requireValidName(name);
        products = List.copyOf(products);
    }

    public static String requireValidName(String name) {
        return Franchise.requireText(name, "El nombre de la sucursal es obligatorio");
    }

    /**
     * Producto con mas stock de la sucursal. En empate gana el nombre menor (alfabetico)
     * para que el resultado sea determinista.
     */
    public Optional<BranchTopProduct> topStockProduct() {
        return products.stream()
                .max(Comparator.comparingInt(Product::stock)
                        .thenComparing(Product::name, Comparator.reverseOrder()))
                .map(p -> new BranchTopProduct(id, name, p.id(), p.name(), p.stock()));
    }
}

