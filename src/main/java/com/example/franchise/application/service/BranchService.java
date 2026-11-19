package com.example.franchise.application.service;

import com.example.franchise.application.port.in.BranchUseCase;
import com.example.franchise.application.port.out.FranchiseRepositoryPort;
import com.example.franchise.domain.model.Branch;

import reactor.core.publisher.Mono;

public class BranchService implements BranchUseCase {

    private final FranchiseRepositoryPort repository;

    public BranchService(FranchiseRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Branch> add(String franchiseId, String name) {
        return Mono.defer(() -> repository.addBranch(franchiseId, Branch.requireValidName(name)));
    }

    @Override
    public Mono<Branch> rename(String franchiseId, String branchId, String name) {
        return Mono.defer(() -> repository.renameBranch(franchiseId, branchId, Branch.requireValidName(name)));
    }
}
