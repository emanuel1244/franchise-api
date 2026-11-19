package com.example.franchise.application.service;

import com.example.franchise.application.port.in.ProductUseCase;
import com.example.franchise.application.port.out.FranchiseRepositoryPort;
import com.example.franchise.domain.model.Product;

import reactor.core.publisher.Mono;

public class ProductService implements ProductUseCase {

    private final FranchiseRepositoryPort repository;

    public ProductService(FranchiseRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Product> add(String franchiseId, String branchId, String name, int stock) {
        return Mono.defer(() -> repository.addProduct(franchiseId, branchId,
                Product.requireValidName(name), Product.requireValidStock(stock)));
    }

    @Override
    public Mono<Product> rename(String franchiseId, String branchId, String productId, String name) {
        return Mono.defer(() -> repository.renameProduct(franchiseId, branchId, productId,
                Product.requireValidName(name)));
    }

    @Override
    public Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock) {
        return Mono.defer(() -> repository.updateStock(franchiseId, branchId, productId,
                Product.requireValidStock(stock)));
    }

    @Override
    public Mono<Void> delete(String franchiseId, String branchId, String productId) {
        return Mono.defer(() -> repository.deleteProduct(franchiseId, branchId, productId));
    }
}
