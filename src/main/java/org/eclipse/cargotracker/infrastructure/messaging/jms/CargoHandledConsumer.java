package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Consumes messages from Amazon SQS and delegates notification of handled cargo
 * to the tracking service. Replaces JMS queue dependency with native Amazon SQS
 * integration to eliminate dependency on traditional message brokers and leverage
 * cloud-native, fully managed queuing.
 */
public class CargoHandledConsumer {

  private static final String CARGO_HANDLED_QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  /**
   * Polls Amazon SQS for cargo handled messages and processes them.
   * This method replaces the JMS MessageListener.onMessage() pattern
   * with SQS polling for cloud-native message consumption.
   */
  public void pollAndProcess() {
    String queueUrl = System.getenv(CARGO_HANDLED_QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isEmpty()) {
      logger.log(Level.WARNING, "SQS queue URL not configured via environment variable: {0}",
          CARGO_HANDLED_QUEUE_URL_ENV);
      return;
    }

    try (SqsClient sqsClient = SqsClient.builder().build()) {
      ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(10)
          .waitTimeSeconds(20) // Long polling for efficiency
          .build();

      ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
      List<Message> messages = response.messages();

      for (Message message : messages) {
        processMessage(sqsClient, queueUrl, message);
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error polling SQS queue for cargo handled messages", e);
    }
  }

  private void processMessage(SqsClient sqsClient, String queueUrl, Message message) {
    try {
      String trackingIdString = message.body();
      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

      // Delete the message after successful processing
      DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle())
          .build();
      sqsClient.deleteMessage(deleteRequest);

      logger.log(Level.INFO, "Successfully processed cargo handled message for tracking ID: {0}",
          trackingIdString);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error processing SQS message: {0}", e.getMessage());
      // Message will become visible again after visibility timeout for retry
    }
  }
}
