package com.example.franchise.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.franchise.application.port.in.BranchUseCase;
import com.example.franchise.application.port.in.FranchiseUseCase;
import com.example.franchise.application.port.in.ProductUseCase;
import com.example.franchise.domain.model.Branch;
import com.example.franchise.domain.model.BranchTopProduct;
import com.example.franchise.domain.model.Franchise;
import com.example.franchise.domain.model.Product;
import com.example.franchise.infrastructure.adapter.in.web.Requests.CreateProductRequest;
import com.example.franchise.infrastructure.adapter.in.web.Requests.NameRequest;
import com.example.franchise.infrastructure.adapter.in.web.Requests.StockRequest;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Adaptador de entrada REST. Solo traduce HTTP a casos de uso. */
@RestController
@RequestMapping("/api/franchises")
public class FranchiseController {

    private final FranchiseUseCase franchises;
    private final BranchUseCase branches;
    private final ProductUseCase products;

    public FranchiseController(FranchiseUseCase franchises, BranchUseCase branches, ProductUseCase products) {
        this.franchises = franchises;
        this.branches = branches;
        this.products = products;
    }

    // Franquicias

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Franchise> createFranchise(@Valid @RequestBody NameRequest body) {
        return franchises.create(body.name());
    }

    @GetMapping("/{franchiseId}")
    public Mono<Franchise> getFranchise(@PathVariable String franchiseId) {
        return franchises.get(franchiseId);
    }

    @PutMapping("/{franchiseId}")
    public Mono<Franchise> renameFranchise(@PathVariable String franchiseId, @Valid @RequestBody NameRequest body) {
        return franchises.rename(franchiseId, body.name());
    }

    @GetMapping("/{franchiseId}/top-stock-products")
    public Flux<BranchTopProduct> topStockProducts(@PathVariable String franchiseId) {
        return franchises.topStockProductPerBranch(franchiseId);
    }

    // Sucursales

    @PostMapping("/{franchiseId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Branch> addBranch(@PathVariable String franchiseId, @Valid @RequestBody NameRequest body) {
        return branches.add(franchiseId, body.name());
    }

    @PutMapping("/{franchiseId}/branches/{branchId}")
    public Mono<Branch> renameBranch(@PathVariable String franchiseId, @PathVariable String branchId,
                                     @Valid @RequestBody NameRequest body) {
        return branches.rename(franchiseId, branchId, body.name());
    }

    // Productos

    @PostMapping("/{franchiseId}/branches/{branchId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Product> addProduct(@PathVariable String franchiseId, @PathVariable String branchId,
                                    @Valid @RequestBody CreateProductRequest body) {
        return products.add(franchiseId, branchId, body.name(), body.stock());
    }

    @PutMapping("/{franchiseId}/branches/{branchId}/products/{productId}")
    public Mono<Product> renameProduct(@PathVariable String franchiseId, @PathVariable String branchId,
                                       @PathVariable String productId, @Valid @RequestBody NameRequest body) {
        return products.rename(franchiseId, branchId, productId, body.name());
    }

    @PatchMapping("/{franchiseId}/branches/{branchId}/products/{productId}/stock")
    public Mono<Product> updateStock(@PathVariable String franchiseId, @PathVariable String branchId,
                                     @PathVariable String productId, @Valid @RequestBody StockRequest body) {
        return products.updateStock(franchiseId, branchId, productId, body.stock());
    }

    @DeleteMapping("/{franchiseId}/branches/{branchId}/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteProduct(@PathVariable String franchiseId, @PathVariable String branchId,
                                    @PathVariable String productId) {
        return products.delete(franchiseId, branchId, productId);
    }
}
