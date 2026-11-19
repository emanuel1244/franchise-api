package com.example.franchise.infrastructure.adapter.out.dynamodb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param tableName   nombre de la tabla DynamoDB
 * @param region      region AWS
 * @param endpoint    endpoint opcional (DynamoDB Local). Vacio = AWS real
 * @param createTable si es true crea la tabla al arrancar cuando no existe (solo desarrollo local)
 */
@ConfigurationProperties(prefix = "app.dynamodb")
public record DynamoProperties(String tableName, String region, String endpoint, boolean createTable) {
}
