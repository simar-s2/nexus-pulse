package com.nexuspulse.backend.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuspulse.backend.exception.DownstreamUnavailableException;
import com.nexuspulse.backend.exception.DuplicateEventException;
import com.nexuspulse.backend.model.EventPayload;
import com.nexuspulse.backend.model.EventStatus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * AGENTS.md COMPLIANT Event Publisher Service
 */
@Service
public class EventPublisherService {
    
    private static final Logger logger = LoggerFactory.getLogger(EventPublisherService.class);
    
    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String queueUrl;
    
    private final Counter receivedCounter;
    private final Counter duplicateCounter;
    private final Counter failureCounter;
    
    public EventPublisherService(
            DynamoDbClient dynamoDbClient,
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.sqs.queue-url}") String queueUrl) {
        this.dynamoDbClient = dynamoDbClient;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.queueUrl = queueUrl;
        
        this.receivedCounter = meterRegistry.counter("events.received");
        this.duplicateCounter = meterRegistry.counter("events.duplicate");
        this.failureCounter = meterRegistry.counter("events.failure");
    }
    
    public void publishEvent(EventPayload payload, String traceId) {
        receivedCounter.increment();
        MDC.put("traceId", traceId);
        MDC.put("eventId", payload.eventId());
        
        try {
            // STEP 1: ATOMIC LOCK
            try {
                persistWithStatus(payload, traceId, EventStatus.RECEIVED);
            } catch (ConditionalCheckFailedException e) {
                // FIX: This catch block will now trigger correctly because the Key collision occurs
                duplicateCounter.increment();
                logger.warn("Duplicate event detected. eventId={}", payload.eventId());
                throw new DuplicateEventException("Event already processed: " + payload.eventId(), traceId);
            }
            
            // STEP 2: PUBLISH TO SQS
            try {
                publishToSqs(payload, traceId);
            } catch (Exception e) {
                failureCounter.increment();
                logger.error("SQS publish failed after DynamoDB lock.", e);
                throw new DownstreamUnavailableException(
                    payload.eventId(), traceId, "Failed to queue event", e);
            }
            
            // STEP 3: UPDATE STATUS
            updateStatusToQueued(payload.eventId(), payload.idempotencyKey());
            
            logger.info("Event published successfully. eventId={}", payload.eventId());
                
        } finally {
            MDC.clear();
        }
    }
    
    private void persistWithStatus(EventPayload payload, String traceId, EventStatus status) {
        long ttl = Instant.now().plusSeconds(30 * 24 * 60 * 60).getEpochSecond();
        
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("eventId", AttributeValue.builder().s(payload.eventId()).build());
        
        // FIX: Use idempotencyKey as the Sort Key (mapped to 'timestamp' column)
        // This guarantees that (eventId + idempotencyKey) is unique.
        // Previously, using Instant.now() created a new row every time.
        item.put("timestamp", AttributeValue.builder().s(payload.idempotencyKey()).build());
        
        item.put("status", AttributeValue.builder().s(status.name()).build());
        item.put("idempotencyKey", AttributeValue.builder().s(payload.idempotencyKey()).build());
        item.put("type", AttributeValue.builder().s(payload.type()).build());
        item.put("traceId", AttributeValue.builder().s(traceId).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
        
        PutItemRequest request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            // FIX: Ensure this exact PK + SK combination does not exist
            .conditionExpression("attribute_not_exists(eventId)")
            .build();
        
        dynamoDbClient.putItem(request);
    }
    
    private void publishToSqs(EventPayload payload, String traceId) {
        try {
            String messageBody = objectMapper.writeValueAsString(payload);
            
            Map<String, MessageAttributeValue> attributes = new HashMap<>();
            attributes.put("traceId", MessageAttributeValue.builder().dataType("String").stringValue(traceId).build());
            attributes.put("eventType", MessageAttributeValue.builder().dataType("String").stringValue(payload.type()).build());
            
            SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .messageAttributes(attributes)
                .build();
            
            sqsClient.sendMessage(request);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish to SQS", e);
        }
    }
    
    private void updateStatusToQueued(String eventId, String idempotencyKey) {
        try {
            // FIX: Use the correct Composite Key (PK=eventId, SK=idempotencyKey)
            Map<String, AttributeValue> key = Map.of(
                "eventId", AttributeValue.builder().s(eventId).build(),
                "timestamp", AttributeValue.builder().s(idempotencyKey).build()
            );
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #status = :queued")
                .conditionExpression("#status = :received")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                    ":queued", AttributeValue.builder().s(EventStatus.QUEUED.name()).build(),
                    ":received", AttributeValue.builder().s(EventStatus.RECEIVED.name()).build()
                ))
                .build();
            
            dynamoDbClient.updateItem(request);
        } catch (Exception e) {
            logger.warn("Failed to update status to QUEUED. eventId={}", eventId, e);
        }
    }
}