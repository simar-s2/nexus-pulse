import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sqs from 'aws-cdk-lib/aws-sqs';

export class NexusPulseStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // ========================================================================
    // 1. Storage Layer (DynamoDB)
    // Goals: Metadata storage, Idempotency checks
    // AGENTS.md 5.5: TTL enabled, Conditional writes support (implicit in table)
    // ========================================================================
    const eventsTable = new dynamodb.Table(this, 'EventsTable', {
      partitionKey: { 
        name: 'eventId', 
        type: dynamodb.AttributeType.STRING 
      },
      sortKey: { 
        name: 'timestamp', 
        type: dynamodb.AttributeType.STRING 
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST, // Cost Awareness
      timeToLiveAttribute: 'ttl', // AGENTS.md 1.2: Auto-delete old events
      
      // STRICT: In a real production environment, this should be RETAIN.
      // For this lab/dev environment, we use DESTROY to clean up cleanly.
      removalPolicy: cdk.RemovalPolicy.DESTROY, 
    });

    // ========================================================================
    // 2. Messaging Layer (SQS)
    // Goals: Async-first, Resilience
    // AGENTS.md 5.3: DLQ is mandatory, Visibility > Processing time
    // ========================================================================
    
    // 2.1 Dead Letter Queue (DLQ)
    const deadLetterQueue = new sqs.Queue(this, 'EventsDLQ', {
      queueName: 'nexus-pulse-dlq',
      retentionPeriod: cdk.Duration.days(14), // Max retention for debugging
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // 2.2 Main Queue
    const mainQueue = new sqs.Queue(this, 'EventsQueue', {
      queueName: 'nexus-pulse-queue',
      // AGENTS.md 1.2: VisibilityTimeout 30s
      visibilityTimeout: cdk.Duration.seconds(30), 
      deadLetterQueue: {
        queue: deadLetterQueue,
        maxReceiveCount: 3, // AGENTS.md 1.2: Retry 3 times then DLQ
      },
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // ========================================================================
    // 3. Outputs (Operational Visibility)
    // ========================================================================
    new cdk.CfnOutput(this, 'DynamoDBTableName', {
      value: eventsTable.tableName,
      description: 'The name of the DynamoDB table for event metadata',
    });

    new cdk.CfnOutput(this, 'SQSQueueUrl', {
      value: mainQueue.queueUrl,
      description: 'The URL of the main SQS queue',
    });
  }
}