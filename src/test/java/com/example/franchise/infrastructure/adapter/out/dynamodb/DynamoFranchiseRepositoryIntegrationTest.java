package com.example.franchise.infrastructure.adapter.out.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.example.franchise.domain.exception.NotFoundException;
import com.example.franchise.domain.model.Franchise;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Prueba de integracion del adaptador contra DynamoDB Local.
 * Se ejecuta solo si existe la variable DYNAMODB_IT_ENDPOINT (p. ej. http://localhost:8000).
 * Crea una tabla temporal y la borra al terminar.
 */
@EnabledIfEnvironmentVariable(named = "DYNAMODB_IT_ENDPOINT", matches = ".+")
class DynamoFranchiseRepositoryIntegrationTest {

    private static final String TABLE = "franchises-it-" + UUID.randomUUID();
    private static DynamoDbAsyncClient client;
    private static DynamoFranchiseRepository repo;

    @BeforeAll
    static void setUp() {
        client = DynamoDbAsyncClient.builder()
                .endpointOverride(URI.create(System.getenv("DYNAMODB_IT_ENDPOINT")))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
        client.createTable(b -> b.tableName(TABLE)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()))
                .join();
        repo = new DynamoFranchiseRepository(client, new DynamoProperties(TABLE, "us-east-1", null, false));
    }

    @AfterAll
    static void tearDown() {
        client.deleteTable(b -> b.tableName(TABLE)).join();
        client.close();
    }

    @Test
    void fullFlowPersistsTree() {
        Mono<Franchise> flow = repo.createFranchise("Cadena").flatMap(f ->
                repo.addBranch(f.id(), "Norte").flatMap(b ->
                        repo.addProduct(f.id(), b.id(), "Cafe", 3).flatMap(p ->
                                repo.updateStock(f.id(), b.id(), p.id(), 30)
                                        .then(repo.renameProduct(f.id(), b.id(), p.id(), "Cafe molido"))
                                        .then(repo.renameBranch(f.id(), b.id(), "Norte 1"))
                                        .then(repo.addProduct(f.id(), b.id(), "Te", 1))
                                        .flatMap(t -> repo.deleteProduct(f.id(), b.id(), t.id()))
                                        .then(repo.renameFranchise(f.id(), "Cadena SA")))));

        StepVerifier.create(flow)
                .assertNext(f -> {
                    assertEquals("Cadena SA", f.name());
                    assertEquals(1, f.branches().size());
                    assertEquals("Norte 1", f.branches().get(0).name());
                    assertEquals(List.of("Cafe molido"),
                            f.branches().get(0).products().stream().map(p -> p.name()).toList());
                    assertEquals(30, f.branches().get(0).products().get(0).stock());
                })
                .verifyComplete();
    }

    @Test
    void missingResourcesRaiseNotFound() {
        String missing = UUID.randomUUID().toString();
        StepVerifier.create(repo.findFranchise(missing)).expectError(NotFoundException.class).verify();
        StepVerifier.create(repo.addBranch(missing, "X")).expectError(NotFoundException.class).verify();
        StepVerifier.create(repo.renameFranchise(missing, "X")).expectError(NotFoundException.class).verify();
        StepVerifier.create(repo.addProduct(missing, missing, "X", 1)).expectError(NotFoundException.class).verify();
        StepVerifier.create(repo.updateStock(missing, missing, missing, 1)).expectError(NotFoundException.class).verify();
        StepVerifier.create(repo.deleteProduct(missing, missing, missing)).expectError(NotFoundException.class).verify();
    }
}
