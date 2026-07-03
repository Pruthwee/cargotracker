package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.HandlingEventService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.CannotCreateHandlingEventException;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.LocalDateTime;

/**
 * Consumes handling event registration attempt messages from Amazon SQS
 * and delegates to proper registration. Replaces JMS queue dependency with
 * native Amazon SQS integration for cloud-native, fully managed queuing.
 */
public class HandlingEventRegistrationAttemptConsumer {

  private static final String HANDLING_EVENT_QUEUE_URL_ENV = "HANDLING_EVENT_QUEUE_URL";

  @Inject private Logger logger;
  @Inject private HandlingEventService handlingEventService;

  /**
   * Polls Amazon SQS for handling event registration attempt messages and processes them.
   */
  public void pollAndProcess() {
    String queueUrl = System.getenv(HANDLING_EVENT_QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isEmpty()) {
      logger.log(Level.WARNING, "SQS queue URL not configured via environment variable: {0}",
          HANDLING_EVENT_QUEUE_URL_ENV);
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
      logger.log(Level.SEVERE, "Error polling SQS queue for handling event messages", e);
    }
  }

  private void processMessage(SqsClient sqsClient, String queueUrl, Message message) {
    try {
      // Parse the serialized attempt from message body
      // Format: trackingId|voyageNumber|unLocode|type|completionTime|registrationTime
      String[] parts = message.body().split("\\|", -1);
      if (parts.length >= 4) {
        TrackingId trackingId = new TrackingId(parts[0]);
        VoyageNumber voyageNumber = parts[1].isEmpty() ? null : new VoyageNumber(parts[1]);
        UnLocode unLocode = new UnLocode(parts[2]);
        HandlingEvent.Type type = HandlingEvent.Type.valueOf(parts[3]);
        LocalDateTime completionTime = parts.length > 4 ? LocalDateTime.parse(parts[4]) : LocalDateTime.now();

        handlingEventService.registerHandlingEvent(
            completionTime, trackingId, voyageNumber, unLocode, type);

        // Delete the message after successful processing
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build();
        sqsClient.deleteMessage(deleteRequest);

        logger.log(Level.INFO, "Successfully processed handling event registration attempt for: {0}",
            trackingId.getIdString());
      }
    } catch (CannotCreateHandlingEventException e) {
      logger.log(Level.WARNING, "Cannot create handling event from SQS message: {0}", e.getMessage());
      // Delete poison message to prevent infinite retry
      try {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build();
        sqsClient.deleteMessage(deleteRequest);
      } catch (Exception deleteEx) {
        logger.log(Level.SEVERE, "Error deleting poison message from SQS", deleteEx);
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error processing SQS message: {0}", e.getMessage());
      // Message will become visible again after visibility timeout for retry
    }
  }
}
