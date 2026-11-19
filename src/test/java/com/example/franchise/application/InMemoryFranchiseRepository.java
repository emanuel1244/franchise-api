package com.example.franchise.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.franchise.application.port.out.FranchiseRepositoryPort;
import com.example.franchise.domain.exception.NotFoundException;
import com.example.franchise.domain.model.Branch;
import com.example.franchise.domain.model.Franchise;
import com.example.franchise.domain.model.Product;

import reactor.core.publisher.Mono;

/** Adaptador en memoria del puerto de persistencia, solo para pruebas. */
class InMemoryFranchiseRepository implements FranchiseRepositoryPort {

    private final Map<String, String> franchises = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> branches = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, Product>>> products = new LinkedHashMap<>();

    @Override
    public Mono<Franchise> createFranchise(String name) {
        String id = UUID.randomUUID().toString();
        franchises.put(id, name);
        branches.put(id, new LinkedHashMap<>());
        products.put(id, new LinkedHashMap<>());
        return Mono.just(new Franchise(id, name, List.of()));
    }

    @Override
    public Mono<Franchise> findFranchise(String franchiseId) {
        if (!franchises.containsKey(franchiseId)) {
            return Mono.error(new NotFoundException("Franquicia no encontrada: " + franchiseId));
        }
        List<Branch> result = new ArrayList<>();
        branches.get(franchiseId).forEach((bid, bname) -> result.add(new Branch(bid, bname,
                List.copyOf(products.get(franchiseId).getOrDefault(bid, Map.of()).values()))));
        return Mono.just(new Franchise(franchiseId, franchises.get(franchiseId), result));
    }

    @Override
    public Mono<Franchise> renameFranchise(String franchiseId, String name) {
        if (!franchises.containsKey(franchiseId)) {
            return Mono.error(new NotFoundException("Franquicia no encontrada"));
        }
        franchises.put(franchiseId, name);
        return findFranchise(franchiseId);
    }

    @Override
    public Mono<Branch> addBranch(String franchiseId, String name) {
        if (!franchises.containsKey(franchiseId)) {
            return Mono.error(new NotFoundException("Franquicia no encontrada"));
        }
        String id = UUID.randomUUID().toString();
        branches.get(franchiseId).put(id, name);
        return Mono.just(new Branch(id, name, List.of()));
    }

    @Override
    public Mono<Branch> renameBranch(String franchiseId, String branchId, String name) {
        Map<String, String> bs = branches.get(franchiseId);
        if (bs == null || !bs.containsKey(branchId)) {
            return Mono.error(new NotFoundException("Sucursal no encontrada"));
        }
        bs.put(branchId, name);
        return Mono.just(new Branch(branchId, name, List.of()));
    }

    @Override
    public Mono<Product> addProduct(String franchiseId, String branchId, String name, int stock) {
        Map<String, String> bs = branches.get(franchiseId);
        if (bs == null || !bs.containsKey(branchId)) {
            return Mono.error(new NotFoundException("Sucursal no encontrada"));
        }
        Product p = new Product(UUID.randomUUID().toString(), name, stock);
        products.get(franchiseId).computeIfAbsent(branchId, k -> new LinkedHashMap<>()).put(p.id(), p);
        return Mono.just(p);
    }

    @Override
    public Mono<Product> renameProduct(String franchiseId, String branchId, String productId, String name) {
        return find(franchiseId, branchId, productId).map(p -> save(franchiseId, branchId,
                new Product(p.id(), name, p.stock())));
    }

    @Override
    public Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock) {
        return find(franchiseId, branchId, productId).map(p -> save(franchiseId, branchId,
                new Product(p.id(), p.name(), stock)));
    }

    @Override
    public Mono<Void> deleteProduct(String franchiseId, String branchId, String productId) {
        return find(franchiseId, branchId, productId)
                .doOnNext(p -> products.get(franchiseId).get(branchId).remove(productId)).then();
    }

    private Mono<Product> find(String franchiseId, String branchId, String productId) {
        Product p = products.getOrDefault(franchiseId, Map.of()).getOrDefault(branchId, Map.of()).get(productId);
        return p == null ? Mono.error(new NotFoundException("Producto no encontrado")) : Mono.just(p);
    }

    private Product save(String franchiseId, String branchId, Product p) {
        products.get(franchiseId).get(branchId).put(p.id(), p);
        return p;
    }
}
