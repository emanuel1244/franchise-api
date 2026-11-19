package com.example.franchise.application.port.in;

import com.example.franchise.domain.model.BranchTopProduct;
import com.example.franchise.domain.model.Franchise;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FranchiseUseCase {

    Mono<Franchise> create(String name);

    Mono<Franchise> get(String franchiseId);

    Mono<Franchise> rename(String franchiseId, String name);

    /** Producto con mas stock por cada sucursal de la franquicia. */
    Flux<BranchTopProduct> topStockProductPerBranch(String franchiseId);
}
