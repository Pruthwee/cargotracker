package org.eclipse.cargotracker.infrastructure.messaging.sqs;

import java.io.IOException;
import java.util.List;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Amazon SQS in place of the JMS CargoHandledQueue.
 *
 * <p>The queue URL is read from the SQS_CARGO_HANDLED_QUEUE_URL environment variable; the region
 * and the credentials come from the AWS SDK default chain. The client is built on first use, so the
 * application starts when SQS cannot be reached. Every failure is reported as an {@link
 * IOException}.
 */
@ApplicationScoped
public class SqsQueue {

  public static final String CARGO_HANDLED_QUEUE_URL_ENV = "SQS_CARGO_HANDLED_QUEUE_URL";

  private static final int MAX_MESSAGES = 10;
  private static final int WAIT_TIME_SECONDS = 20;

  private volatile SqsClient sqsClient;

  public void send(String queueUrl, String body) throws IOException {
    try {
      client()
          .sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());
    } catch (SdkException e) {
      throw new IOException("Cannot send message to SQS queue", e);
    }
  }

  /** Long-polls the queue for messages. */
  public List<Message> receive(String queueUrl) throws IOException {
    try {
      return client()
          .receiveMessage(
              ReceiveMessageRequest.builder()
                  .queueUrl(queueUrl)
                  .maxNumberOfMessages(MAX_MESSAGES)
                  .waitTimeSeconds(WAIT_TIME_SECONDS)
                  .build())
          .messages();
    } catch (SdkException e) {
      throw new IOException("Cannot receive messages from SQS queue", e);
    }
  }

  /** Deletes a message once it was processed. */
  public void delete(String queueUrl, Message message) throws IOException {
    try {
      client()
          .deleteMessage(
              DeleteMessageRequest.builder()
                  .queueUrl(queueUrl)
                  .receiptHandle(message.receiptHandle())
                  .build());
    } catch (SdkException e) {
      throw new IOException("Cannot delete message from SQS queue", e);
    }
  }

  private SqsClient client() {
    SqsClient client = sqsClient;

    if (client == null) {
      synchronized (this) {
        client = sqsClient;

        if (client == null) {
          client = SqsClient.create();
          sqsClient = client;
        }
      }
    }

    return client;
  }

  @PreDestroy
  void close() {
    SqsClient client = sqsClient;

    if (client != null) {
      client.close();
    }
  }
}
