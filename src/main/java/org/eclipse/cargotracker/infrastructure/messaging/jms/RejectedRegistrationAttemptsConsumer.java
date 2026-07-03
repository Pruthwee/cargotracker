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
 * Consumes rejected registration attempt messages from Amazon SQS.
 * Replaces JMS queue dependency with native Amazon SQS integration for
 * cloud-native, fully managed queuing.
 */
public class RejectedRegistrationAttemptsConsumer {

  private static final String REJECTED_REGISTRATION_QUEUE_URL_ENV = "REJECTED_REGISTRATION_QUEUE_URL";

  @Inject private Logger logger;

  /**
   * Polls Amazon SQS for rejected registration attempt messages and processes them.
   */
  public void pollAndProcess() {
    String queueUrl = System.getenv(REJECTED_REGISTRATION_QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isEmpty()) {
      logger.log(Level.WARNING, "SQS queue URL not configured via environment variable: {0}",
          REJECTED_REGISTRATION_QUEUE_URL_ENV);
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
          logger.log(Level.INFO,
              "Rejected registration attempt of cargo with tracking ID {0}.", message.body());

          DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build();
          sqsClient.deleteMessage(deleteRequest);
        } catch (Exception e) {
          logger.log(Level.WARNING, "Error processing rejected registration attempt message.", e);
        }
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error polling SQS queue for rejected registration attempt messages", e);
    }
  }
}
