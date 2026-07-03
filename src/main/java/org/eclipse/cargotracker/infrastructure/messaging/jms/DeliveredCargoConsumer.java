package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Consumes delivered cargo messages from Amazon SQS.
 * Replaces JMS queue dependency with native Amazon SQS integration for
 * cloud-native, fully managed queuing.
 */
public class DeliveredCargoConsumer {

  private static final String DELIVERED_CARGO_QUEUE_URL_ENV = "DELIVERED_CARGO_QUEUE_URL";

  @Inject private Logger logger;

  /**
   * Polls Amazon SQS for delivered cargo messages and processes them.
   */
  public void pollAndProcess() {
    String queueUrl = System.getenv(DELIVERED_CARGO_QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isEmpty()) {
      logger.log(Level.WARNING, "SQS queue URL not configured via environment variable: {0}",
          DELIVERED_CARGO_QUEUE_URL_ENV);
      return;
    }

    try (SqsClient sqsClient = SqsClient.builder().build()) {
      ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(10)
          .waitTimeSeconds(20)
          .build();

      ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
      List<Message> messages = response.messages();

      for (Message message : messages) {
        try {
          logger.log(Level.INFO, "Cargo with tracking ID {0} delivered.", message.body());

          DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build();
          sqsClient.deleteMessage(deleteRequest);
        } catch (Exception e) {
          logger.log(Level.WARNING, "Error processing delivered cargo message.", e);
        }
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error polling SQS queue for delivered cargo messages", e);
    }
  }
}
