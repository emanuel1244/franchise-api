package com.example.franchise.infrastructure.adapter.out.dynamodb;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@Configuration
public class DynamoConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamoConfig.class);

    @Bean(destroyMethod = "close")
    DynamoDbAsyncClient dynamoDbAsyncClient(DynamoProperties props) {
        DynamoDbAsyncClientBuilder builder = DynamoDbAsyncClient.builder()
                .region(Region.of(props.region()));
        if (StringUtils.hasText(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    /** Crea la tabla solo si app.dynamodb.create-table=true (DynamoDB Local). En AWS la crea CloudFormation. */
    @Bean
    ApplicationRunner tableInitializer(DynamoDbAsyncClient client, DynamoProperties props) {
        return args -> {
            if (!props.createTable()) {
                return;
            }
            try {
                client.describeTable(DescribeTableRequest.builder().tableName(props.tableName()).build()).join();
                log.info("Tabla {} ya existe", props.tableName());
            } catch (RuntimeException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (!(cause instanceof ResourceNotFoundException)) {
                    throw e;
                }
                client.createTable(CreateTableRequest.builder()
                        .tableName(props.tableName())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .attributeDefinitions(
                                AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                        .keySchema(
                                KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                        .build()).join();
                log.info("Tabla {} creada", props.tableName());
            }
        };
    }
}
