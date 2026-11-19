package com.example.franchise.application.service;

import com.example.franchise.application.port.in.FranchiseUseCase;
import com.example.franchise.application.port.out.FranchiseRepositoryPort;
import com.example.franchise.domain.model.Branch;
import com.example.franchise.domain.model.BranchTopProduct;
import com.example.franchise.domain.model.Franchise;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FranchiseService implements FranchiseUseCase {

    private final FranchiseRepositoryPort repository;

    public FranchiseService(FranchiseRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Franchise> create(String name) {
        return Mono.defer(() -> repository.createFranchise(Franchise.requireValidName(name)));
    }

    @Override
    public Mono<Franchise> get(String franchiseId) {
        return Mono.defer(() -> repository.findFranchise(franchiseId));
    }

    @Override
    public Mono<Franchise> rename(String franchiseId, String name) {
        return Mono.defer(() -> repository.renameFranchise(franchiseId, Franchise.requireValidName(name)));
    }

    @Override
    public Flux<BranchTopProduct> topStockProductPerBranch(String franchiseId) {
        return Flux.defer(() -> repository.findFranchise(franchiseId))
                .flatMapIterable(Franchise::branches)
                .map(Branch::topStockProduct)
                .flatMapIterable(top -> top.stream().toList());
    }
}
