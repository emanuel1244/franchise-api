package com.example.franchise.domain.model;

/** Producto con mas stock de una sucursal, indicando a que sucursal pertenece. */
public record BranchTopProduct(String branchId, String branchName, String productId, String productName,
                               int stock) {
}
