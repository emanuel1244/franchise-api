package com.example.franchise.application.port.in;

import com.example.franchise.domain.model.Product;

import reactor.core.publisher.Mono;

public interface ProductUseCase {

    Mono<Product> add(String franchiseId, String branchId, String name, int stock);

    Mono<Product> rename(String franchiseId, String branchId, String productId, String name);

    Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock);

    Mono<Void> delete(String franchiseId, String branchId, String productId);
}
