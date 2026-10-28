package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * The Amazon SQS queue that replaces the JMS CargoHandledQueue.
 *
 * <p>The queue URL comes from the SQS_QUEUE_URL environment variable, the region and the
 * credentials from the AWS SDK default chain. The client is built on first use, so the application
 * starts when SQS cannot be reached. The methods throw the SDK's unchecked SdkException, which
 * callers catch.
 */
@ApplicationScoped
public class CargoHandledQueue {

  static final String QUEUE_URL_ENV = "SQS_QUEUE_URL";
  private static final int MAX_MESSAGES = 10;
  private static final int WAIT_TIME_SECONDS = 5;

  private SqsClient sqsClient;

  /** The queue URL, or null when the queue is not configured. */
  public String queueUrl() {
    String queueUrl = System.getenv(QUEUE_URL_ENV);
    return queueUrl == null || queueUrl.trim().isEmpty() ? null : queueUrl.trim();
  }

  public void send(String queueUrl, String trackingId) {
    client()
        .sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(trackingId).build());
  }

  public List<Message> receive(String queueUrl) {
    return client()
        .receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(MAX_MESSAGES)
                .waitTimeSeconds(WAIT_TIME_SECONDS)
                .build())
        .messages();
  }

  public void delete(String queueUrl, Message message) {
    client()
        .deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
  }

  private synchronized SqsClient client() {
    if (sqsClient == null) {
      sqsClient = SqsClient.create();
    }

    return sqsClient;
  }

  @PreDestroy
  public synchronized void close() {
    if (sqsClient != null) {
      sqsClient.close();
      sqsClient = null;
    }
  }
}
