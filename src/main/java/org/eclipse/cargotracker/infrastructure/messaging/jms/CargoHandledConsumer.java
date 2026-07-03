package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consumes SQS messages and delegates notification of misdirected cargo to the tracking service.
 *
 * <p>This is a programmatic hook into the SQS infrastructure to make cargo inspection
 * message-driven.
 */
@ApplicationScoped
public class CargoHandledConsumer {

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  @Inject private SqsClient sqsClient;

  private ScheduledExecutorService executor;
  private String queueUrl = System.getenv("CARGO_HANDLED_QUEUE_URL");

  @PostConstruct
  public void init() {
    executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    executor.scheduleWithFixedDelay(this::pollMessages, 0, 1, TimeUnit.SECONDS);
  }

  private void pollMessages() {
    try {
      ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(10)
          .waitTimeSeconds(20)
          .build();

      ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
      List<Message> messages = response.messages();

      for (Message message : messages) {
        processMessage(message);
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error polling SQS messages", e);
    }
  }

  private void processMessage(Message message) {
    try {
      String trackingIdString = message.body();
      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));
      
      // Delete message after successful processing
      sqsClient.deleteMessage(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle())
          .build());
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error processing SQS message", e);
    }
  }

  @PreDestroy
  public void stop() {
    if (executor != null) {
      executor.shutdown();
    }
  }
}
