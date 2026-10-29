package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.infrastructure.messaging.sqs.SqsQueue;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Consumes the cargo handled messages from the Amazon SQS queue and delegates notification of
 * misdirected cargo to the tracking service.
 *
 * <p>The queue URL is read from the SQS_CARGO_HANDLED_QUEUE_URL environment variable. This replaces
 * the former JMS message-driven bean on the CargoHandledQueue: the queue is polled by a timer, and
 * a message is deleted once the cargo was inspected.
 */
@Singleton
@Startup
public class CargoHandledConsumer {

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  @Inject private SqsQueue sqsQueue;

  @Schedule(second = "*/30", minute = "*", hour = "*", persistent = false)
  public void pollMessages() {
    String queueUrl = System.getenv(SqsQueue.CARGO_HANDLED_QUEUE_URL_ENV);

    if (queueUrl == null || queueUrl.trim().isEmpty()) {
      logger.log(
          Level.FINE, "{0} is not set, not polling for cargo handled messages",
          SqsQueue.CARGO_HANDLED_QUEUE_URL_ENV);
      return;
    }

    try {
      List<Message> messages = sqsQueue.receive(queueUrl);

      for (Message message : messages) {
        onMessage(queueUrl, message);
      }
    } catch (IOException | SdkException e) {
      logger.log(Level.SEVERE, "Error procesing SQS message", e);
    }
  }

  private void onMessage(String queueUrl, Message message) throws IOException {
    try {
      String trackingIdString = message.body();

      cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));
    } catch (RuntimeException e) {
      // The message is not deleted, so it becomes visible again, as an undelivered JMS message did.
      logger.log(Level.SEVERE, "Error procesing SQS message", e);
      return;
    }

    sqsQueue.delete(queueUrl, message);
  }
}
