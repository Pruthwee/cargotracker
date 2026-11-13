package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
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
 * <p>This is a programmatic hook into the SQS infrastructure to make cargo inspection message-driven.
 */
@ApplicationScoped
public class CargoHandledConsumer {

  private static final String QUEUE_URL_PROPERTY = "APP_SQS_QUEUE_URL";
  private static final String DEFAULT_QUEUE_URL = "http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path";

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  private SqsClient sqsClient;
  private String queueUrl;

  /**
   * Lazily initializes the SQS client and queue URL on first use, preserving the old failure path.
   */
  private void initializeSqsClient() {
    if (sqsClient == null) {
      try {
        this.queueUrl = System.getenv(QUEUE_URL_PROPERTY);
        if (queueUrl == null || queueUrl.isEmpty()) {
          this.queueUrl = DEFAULT_QUEUE_URL;
        }
        this.sqsClient = SqsClient.builder().build();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Could not initialize SQS client, using default queue URL", e);
        this.queueUrl = DEFAULT_QUEUE_URL;
        this.sqsClient = null;
      }
    }
  }

  /**
   * Polls the SQS queue for messages and processes them, replacing the JMS onMessage behavior.
   */
  public void receiveMessages() {
    initializeSqsClient();

    if (sqsClient == null) {
      logger.log(Level.WARNING, "SQS client not available, skipping message polling");
      return;
    }

    try {
      ReceiveMessageRequest receiveRequest =
          ReceiveMessageRequest.builder()
              .queueUrl(queueUrl)
              .maxNumberOfMessages(10)
              .waitTimeSeconds(20)
              .build();

      List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

      for (Message message : messages) {
        try {
          String trackingIdString = message.body();
          cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));

          DeleteMessageRequest deleteRequest =
              DeleteMessageRequest.builder()
                  .queueUrl(queueUrl)
                  .receiptHandle(message.receiptHandle())
                  .build();
          sqsClient.deleteMessage(deleteRequest);
        } catch (Exception e) {
          logger.log(Level.SEVERE, "Error processing SQS message", e);
        }
      }
    } catch (SdkException e) {
      logger.log(Level.SEVERE, "Error receiving SQS messages", e);
    }
  }
}
