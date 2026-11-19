package com.example.franchise.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.franchise.application.port.in.BranchUseCase;
import com.example.franchise.application.port.in.FranchiseUseCase;
import com.example.franchise.application.port.in.ProductUseCase;
import com.example.franchise.application.port.out.FranchiseRepositoryPort;
import com.example.franchise.application.service.BranchService;
import com.example.franchise.application.service.FranchiseService;
import com.example.franchise.application.service.ProductService;

/** Composicion: la capa de aplicacion no depende de Spring, se cablea aqui. */
@Configuration
public class UseCaseConfig {

    @Bean
    FranchiseUseCase franchiseUseCase(FranchiseRepositoryPort repository) {
        return new FranchiseService(repository);
    }

    @Bean
    BranchUseCase branchUseCase(FranchiseRepositoryPort repository) {
        return new BranchService(repository);
    }

    @Bean
    ProductUseCase productUseCase(FranchiseRepositoryPort repository) {
        return new ProductService(repository);
    }
}
