package com.example.franchise.application.port.in;

import com.example.franchise.domain.model.Branch;

import reactor.core.publisher.Mono;

public interface BranchUseCase {

    Mono<Branch> add(String franchiseId, String name);

    Mono<Branch> rename(String franchiseId, String branchId, String name);
}
