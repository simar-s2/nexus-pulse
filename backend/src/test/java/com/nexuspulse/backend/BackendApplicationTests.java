package com.nexuspulse.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@SpringBootTest
class BackendApplicationTests {

    // AGENTS.md 4.5: Mock AWS clients.
    // This prevents the application from trying to connect to real AWS
    // during tests, which fixes the "Failed to load ApplicationContext" error.
    
    @MockBean
    private DynamoDbClient dynamoDbClient;

    @MockBean
    private SqsClient sqsClient;

    @Test
    void contextLoads() {
        // The context will now load successfully because the 
        // heavyweight AWS clients are replaced with mocks.
    }

}