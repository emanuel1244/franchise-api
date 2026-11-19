package com.example.franchise.application.port.out;

import com.example.franchise.domain.model.Branch;
import com.example.franchise.domain.model.Franchise;
import com.example.franchise.domain.model.Product;

import reactor.core.publisher.Mono;

/**
 * Puerto de salida de persistencia. Todo metodo que referencia un recurso inexistente
 * debe fallar con {@link com.example.franchise.domain.exception.NotFoundException}.
 */
public interface FranchiseRepositoryPort {

    Mono<Franchise> createFranchise(String name);

    Mono<Franchise> findFranchise(String franchiseId);

    Mono<Franchise> renameFranchise(String franchiseId, String name);

    Mono<Branch> addBranch(String franchiseId, String name);

    Mono<Branch> renameBranch(String franchiseId, String branchId, String name);

    Mono<Product> addProduct(String franchiseId, String branchId, String name, int stock);

    Mono<Product> renameProduct(String franchiseId, String branchId, String productId, String name);

    Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock);

    Mono<Void> deleteProduct(String franchiseId, String branchId, String productId);
}
