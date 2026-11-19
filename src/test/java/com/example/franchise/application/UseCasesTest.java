package com.example.franchise.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.franchise.application.service.BranchService;
import com.example.franchise.application.service.FranchiseService;
import com.example.franchise.application.service.ProductService;
import com.example.franchise.domain.exception.NotFoundException;
import com.example.franchise.domain.model.Branch;
import com.example.franchise.domain.model.BranchTopProduct;
import com.example.franchise.domain.model.Franchise;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UseCasesTest {

    private final InMemoryFranchiseRepository repo = new InMemoryFranchiseRepository();
    private final FranchiseService franchises = new FranchiseService(repo);
    private final BranchService branches = new BranchService(repo);
    private final ProductService products = new ProductService(repo);

    @Test
    void topStockPerBranchSkipsEmptyBranches() {
        Mono<List<BranchTopProduct>> flow = franchises.create("F")
                .flatMap(f -> branches.add(f.id(), "Norte").flatMap(b1 ->
                        products.add(f.id(), b1.id(), "A", 5)
                                .then(products.add(f.id(), b1.id(), "B", 9))
                                .then(branches.add(f.id(), "Sur"))
                                .then(franchises.topStockProductPerBranch(f.id()).collectList())));

        StepVerifier.create(flow)
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("Norte", list.get(0).branchName());
                    assertEquals("B", list.get(0).productName());
                    assertEquals(9, list.get(0).stock());
                })
                .verifyComplete();
    }

    @Test
    void updateStockAndDeleteProduct() {
        Mono<Franchise> flow = franchises.create("F").flatMap(f -> branches.add(f.id(), "N").flatMap(b ->
                products.add(f.id(), b.id(), "P", 1).flatMap(p ->
                        products.updateStock(f.id(), b.id(), p.id(), 42)
                                .then(products.add(f.id(), b.id(), "Q", 3))
                                .flatMap(q -> products.delete(f.id(), b.id(), q.id()))
                                .then(franchises.get(f.id())))));

        StepVerifier.create(flow)
                .assertNext(f -> {
                    assertEquals(1, f.branches().get(0).products().size());
                    assertEquals(42, f.branches().get(0).products().get(0).stock());
                })
                .verifyComplete();
    }

    @Test
    void failsWhenParentDoesNotExist() {
        StepVerifier.create(branches.add("nope", "X")).expectError(NotFoundException.class).verify();
        StepVerifier.create(franchises.topStockProductPerBranch("nope")).expectError(NotFoundException.class).verify();
    }

    @Test
    void rejectsInvalidInput() {
        StepVerifier.create(franchises.create(" ")).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(products.add("f", "b", "p", -1)).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    void tieIsResolvedAlphabetically() {
        Branch branch = new Branch("b", "Norte", List.of(
                new com.example.franchise.domain.model.Product("1", "Zeta", 7),
                new com.example.franchise.domain.model.Product("2", "Alfa", 7)));
        assertEquals("Alfa", branch.topStockProduct().orElseThrow().productName());
    }
}
