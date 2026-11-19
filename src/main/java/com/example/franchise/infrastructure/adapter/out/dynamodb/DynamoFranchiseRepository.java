package com.example.franchise.infrastructure.adapter.out.dynamodb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.example.franchise.application.port.out.FranchiseRepositoryPort;
import com.example.franchise.domain.exception.NotFoundException;
import com.example.franchise.domain.model.Branch;
import com.example.franchise.domain.model.Franchise;
import com.example.franchise.domain.model.Product;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Adaptador de salida: implementa el puerto de persistencia sobre DynamoDB.
 *
 * Diseno de tabla unica. Todos los items de una franquicia comparten la misma particion:
 * <pre>
 *   pk = F#{franchiseId}   sk = META                -> franquicia
 *   pk = F#{franchiseId}   sk = B#{branchId}        -> sucursal
 *   pk = F#{franchiseId}   sk = P#{branchId}#{pid}  -> producto
 * </pre>
 * Una sola Query por particion recupera el arbol completo de una franquicia.
 */
@Repository
public class DynamoFranchiseRepository implements FranchiseRepositoryPort {

    private static final String META = "META";

    private final DynamoDbAsyncClient client;
    private final String table;

    public DynamoFranchiseRepository(DynamoDbAsyncClient client, DynamoProperties props) {
        this.client = client;
        this.table = props.tableName();
    }

    // ---------- Franquicias ----------

    @Override
    public Mono<Franchise> createFranchise(String name) {
        String id = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = Map.of(
                "pk", s(fpk(id)),
                "sk", s(META),
                "id", s(id),
                "name", s(name));
        return Mono.fromFuture(() -> client.putItem(b -> b.tableName(table).item(item)))
                .thenReturn(new Franchise(id, name, List.of()));
    }

    @Override
    public Mono<Franchise> renameFranchise(String franchiseId, String name) {
        return rename(franchiseId, META, name, "Franquicia no encontrada: " + franchiseId)
                .then(findFranchise(franchiseId));
    }

    @Override
    public Mono<Franchise> findFranchise(String franchiseId) {
        return queryPartition(franchiseId).collectList().flatMap(items -> {
            Map<String, AttributeValue> meta = items.stream()
                    .filter(i -> META.equals(i.get("sk").s())).findFirst().orElse(null);
            if (meta == null) {
                return Mono.error(new NotFoundException("Franquicia no encontrada: " + franchiseId));
            }
            Map<String, List<Product>> productsByBranch = new LinkedHashMap<>();
            Map<String, String> branchNames = new LinkedHashMap<>();
            for (Map<String, AttributeValue> i : items) {
                String sk = i.get("sk").s();
                if (sk.startsWith("B#")) {
                    branchNames.put(i.get("id").s(), i.get("name").s());
                } else if (sk.startsWith("P#")) {
                    productsByBranch.computeIfAbsent(i.get("branchId").s(), k -> new ArrayList<>())
                            .add(toProduct(i));
                }
            }
            List<Branch> branches = branchNames.entrySet().stream()
                    .map(e -> new Branch(e.getKey(), e.getValue(),
                            productsByBranch.getOrDefault(e.getKey(), List.of()).stream()
                                    .sorted(Comparator.comparing(Product::name)).toList()))
                    .sorted(Comparator.comparing(Branch::name)).toList();
            return Mono.just(new Franchise(meta.get("id").s(), meta.get("name").s(), branches));
        });
    }

    // ---------- Sucursales ----------

    @Override
    public Mono<Branch> addBranch(String franchiseId, String name) {
        String id = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = Map.of(
                "pk", s(fpk(franchiseId)),
                "sk", s("B#" + id),
                "id", s(id),
                "name", s(name));
        return transact(exists(franchiseId, META), put(item), "Franquicia no encontrada: " + franchiseId)
                .thenReturn(new Branch(id, name, List.of()));
    }

    @Override
    public Mono<Branch> renameBranch(String franchiseId, String branchId, String name) {
        return rename(franchiseId, "B#" + branchId, name, "Sucursal no encontrada: " + branchId)
                .map(attrs -> new Branch(branchId, attrs.get("name").s(), List.of()));
    }

    // ---------- Productos ----------

    @Override
    public Mono<Product> addProduct(String franchiseId, String branchId, String name, int stock) {
        String id = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = Map.of(
                "pk", s(fpk(franchiseId)),
                "sk", s(productSk(branchId, id)),
                "id", s(id),
                "branchId", s(branchId),
                "name", s(name),
                "stock", n(stock));
        return transact(exists(franchiseId, "B#" + branchId), put(item), "Sucursal no encontrada: " + branchId)
                .thenReturn(new Product(id, name, stock));
    }

    @Override
    public Mono<Product> renameProduct(String franchiseId, String branchId, String productId, String name) {
        return rename(franchiseId, productSk(branchId, productId), name, "Producto no encontrado: " + productId)
                .map(DynamoFranchiseRepository::toProduct);
    }

    @Override
    public Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock) {
        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(table)
                .key(key(franchiseId, productSk(branchId, productId)))
                .updateExpression("SET #s = :s")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeNames(Map.of("#s", "stock"))
                .expressionAttributeValues(Map.of(":s", n(stock)))
                .returnValues(ReturnValue.ALL_NEW)
                .build();
        return Mono.fromFuture(() -> client.updateItem(req))
                .map(r -> toProduct(r.attributes()))
                .onErrorMap(ConditionalCheckFailedException.class,
                        e -> new NotFoundException("Producto no encontrado: " + productId));
    }

    @Override
    public Mono<Void> deleteProduct(String franchiseId, String branchId, String productId) {
        DeleteItemRequest req = DeleteItemRequest.builder()
                .tableName(table)
                .key(key(franchiseId, productSk(branchId, productId)))
                .conditionExpression("attribute_exists(pk)")
                .build();
        return Mono.fromFuture(() -> client.deleteItem(req))
                .onErrorMap(ConditionalCheckFailedException.class,
                        e -> new NotFoundException("Producto no encontrado: " + productId))
                .then();
    }

    // ---------- Helpers ----------

    private Flux<Map<String, AttributeValue>> queryPartition(String franchiseId) {
        QueryRequest req = QueryRequest.builder()
                .tableName(table)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", s(fpk(franchiseId))))
                .consistentRead(true)
                .build();
        return Flux.from(client.queryPaginator(req)).concatMapIterable(r -> r.items());
    }

    private Mono<Map<String, AttributeValue>> rename(String franchiseId, String sk, String name, String notFoundMsg) {
        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(table)
                .key(key(franchiseId, sk))
                .updateExpression("SET #n = :n")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeNames(Map.of("#n", "name"))
                .expressionAttributeValues(Map.of(":n", s(name)))
                .returnValues(ReturnValue.ALL_NEW)
                .build();
        return Mono.fromFuture(() -> client.updateItem(req))
                .map(r -> r.attributes())
                .onErrorMap(ConditionalCheckFailedException.class, e -> new NotFoundException(notFoundMsg));
    }

    /** Escritura atomica: verifica que el padre exista y crea el hijo. */
    private Mono<Void> transact(TransactWriteItem parentCheck, TransactWriteItem write, String notFoundMsg) {
        TransactWriteItemsRequest req = TransactWriteItemsRequest.builder().transactItems(parentCheck, write).build();
        return Mono.fromFuture(() -> client.transactWriteItems(req))
                .onErrorMap(TransactionCanceledException.class, e -> new NotFoundException(notFoundMsg))
                .then();
    }

    private TransactWriteItem exists(String franchiseId, String sk) {
        return TransactWriteItem.builder().conditionCheck(ConditionCheck.builder()
                .tableName(table)
                .key(key(franchiseId, sk))
                .conditionExpression("attribute_exists(pk)")
                .build()).build();
    }

    private TransactWriteItem put(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder().put(Put.builder().tableName(table).item(item).build()).build();
    }

    private static Product toProduct(Map<String, AttributeValue> a) {
        return new Product(a.get("id").s(), a.get("name").s(), Integer.parseInt(a.get("stock").n()));
    }

    private static Map<String, AttributeValue> key(String franchiseId, String sk) {
        return Map.of("pk", s(fpk(franchiseId)), "sk", s(sk));
    }

    private static String fpk(String franchiseId) {
        return "F#" + franchiseId;
    }

    private static String productSk(String branchId, String productId) {
        return "P#" + branchId + "#" + productId;
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static AttributeValue n(int v) {
        return AttributeValue.builder().n(Integer.toString(v)).build();
    }
}
