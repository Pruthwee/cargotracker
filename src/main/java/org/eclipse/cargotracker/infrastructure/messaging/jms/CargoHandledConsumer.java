package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Consumes SQS messages and delegates notification of misdirected cargo to the tracking service.
 *
 * <p>Replaced JMS message-driven bean with a scheduled SQS poller, leveraging Amazon SQS as a
 * fully managed message queue without requiring a traditional message broker.
 */
@Stateless
public class CargoHandledConsumer {

  private static final int MAX_MESSAGES = 10;
  private static final int WAIT_TIME_SECONDS = 20;

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  private SqsClient sqsClient;
  private String queueUrl;
  private final Object initLock = new Object();

  /** Lazily initializes the SQS client and queue URL from environment variables on first use. */
  private void ensureInitialized() {
    if (sqsClient != null) {
      return;
    }
    synchronized (initLock) {
      if (sqsClient != null) {
        return;
      }
      queueUrl = System.getenv("SQS_CARGO_HANDLED_QUEUE_URL");
      if (queueUrl == null || queueUrl.isEmpty()) {
        logger.log(Level.WARNING, "SQS_CARGO_HANDLED_QUEUE_URL not set, SQS polling disabled");
        return;
      }
      try {
        sqsClient = SqsClient.builder().build();
      } catch (SdkException e) {
        logger.log(Level.WARNING, "Failed to create SQS client, SQS polling disabled", e);
      }
    }
  }

  /** Scheduled method that polls SQS for cargo handled messages. */
  @Schedule(second = "*/30", minute = "*", hour = "*")
  public void pollForMessages() {
    ensureInitialized();
    if (sqsClient == null || queueUrl == null) {
      return;
    }

    try {
      ReceiveMessageRequest receiveRequest =
          ReceiveMessageRequest.builder()
              .queueUrl(queueUrl)
              .maxNumberOfMessages(MAX_MESSAGES)
              .waitTimeSeconds(WAIT_TIME_SECONDS)
              .messageAttributeNames("All")
              .build();

      List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

      for (Message message : messages) {
        try {
          String trackingIdString = message.body();
          cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

          // Delete message after successful processing
          DeleteMessageRequest deleteRequest =
              DeleteMessageRequest.builder()
                  .queueUrl(queueUrl)
                  .receiptHandle(message.receiptHandle())
                  .build();
          sqsClient.deleteMessage(deleteRequest);
        } catch (Exception e) {
          logger.log(Level.SEVERE, "Error processing SQS message", e);
          // Message will become visible again after visibility timeout for retry
        }
      }
    } catch (SdkException e) {
      logger.log(Level.SEVERE, "Error receiving SQS messages", e);
    }
  }
}
