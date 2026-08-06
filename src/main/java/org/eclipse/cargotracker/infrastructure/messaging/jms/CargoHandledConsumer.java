package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Polls Amazon SQS and delegates notification of handled cargo to the inspection service.
 *
 * <p>Replaces the former JMS {@code @MessageDriven} bean with a cloud-native SQS polling consumer
 * backed by the AWS SDK for Java v2. The queue URL is supplied via the environment variable
 * {@code CARGO_HANDLED_QUEUE_URL}.
 */
@Singleton
@Startup
public class CargoHandledConsumer {

  /** Environment variable that holds the SQS queue URL for the CargoHandled queue. */
  private static final String QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";

  /** Polling interval in milliseconds (5 seconds). */
  private static final long POLL_INTERVAL_MS = 5_000L;

  /** Maximum number of messages to retrieve per poll (SQS maximum is 10). */
  private static final int MAX_MESSAGES = 10;

  /** SQS long-poll wait time in seconds (reduces empty-response API calls). */
  private static final int WAIT_TIME_SECONDS = 20;

  @Inject
  private Logger logger;

  @Inject
  private CargoInspectionService cargoInspectionService;

  @Inject
  private SqsClient sqsClient;

  @Resource
  private TimerService timerService;

  private String queueUrl;

  @PostConstruct
  public void init() {
    queueUrl = System.getenv(QUEUE_URL_ENV);
    if (queueUrl == null || queueUrl.isBlank()) {
      logger.log(Level.WARNING,
          "Environment variable {0} is not set; CargoHandledConsumer will not poll SQS.",
          QUEUE_URL_ENV);
      return;
    }
    // Schedule a repeating interval timer to drive SQS polling.
    TimerConfig config = new TimerConfig("CargoHandledConsumer-SQS-Poll", false);
    timerService.createIntervalTimer(0L, POLL_INTERVAL_MS, config);
    logger.log(Level.INFO,
        "CargoHandledConsumer started; polling SQS queue: {0}", queueUrl);
  }

  @Timeout
  public void pollQueue(Timer timer) {
    if (queueUrl == null || queueUrl.isBlank()) {
      return;
    }
    try {
      ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(MAX_MESSAGES)
          .waitTimeSeconds(WAIT_TIME_SECONDS)
          .build();

      ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
      List<Message> messages = response.messages();

      for (Message message : messages) {
        processMessage(message);
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error polling SQS queue for CargoHandled messages", e);
    }
  }

  private void processMessage(Message message) {
    String trackingIdString = message.body();
    try {
      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));
      // Delete the message from the queue only after successful processing.
      DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle())
          .build();
      sqsClient.deleteMessage(deleteRequest);
      logger.log(Level.INFO,
          "Successfully processed and deleted SQS message for cargo: {0}", trackingIdString);
    } catch (Exception e) {
      logger.log(Level.SEVERE,
          "Error processing SQS message for cargo: " + trackingIdString
              + ". Message will become visible again for retry.", e);
      // Do NOT delete the message — SQS visibility timeout will make it reappear for retry.
    }
  }

  @PreDestroy
  public void shutdown() {
    logger.log(Level.INFO, "CargoHandledConsumer shutting down.");
  }
}
