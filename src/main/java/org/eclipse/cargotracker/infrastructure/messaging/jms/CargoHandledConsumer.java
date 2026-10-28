package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.CargoInspectionService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Consumes cargo handled messages from Amazon SQS and delegates notification of misdirected cargo
 * to the tracking service.
 *
 * <p>The queue is polled on a timer; a message is deleted from the queue once the cargo was
 * inspected, so a failed inspection is delivered again.
 */
@Singleton
@PermitAll
public class CargoHandledConsumer {

  @Inject private Logger logger;

  @Inject private CargoInspectionService cargoInspectionService;

  @Inject private CargoHandledQueue queue;

  @Schedule(second = "*/10", minute = "*", hour = "*", persistent = false)
  public void pollMessages() {
    String queueUrl = queue.queueUrl();

    if (queueUrl == null) {
      logger.log(
          Level.FINE,
          "{0} is not set, not polling for handled cargo",
          CargoHandledQueue.QUEUE_URL_ENV);
      return;
    }

    try {
      for (Message message : queue.receive(queueUrl)) {
        onMessage(message);
        queue.delete(queueUrl, message);
      }
    } catch (SdkException e) {
      logger.log(Level.SEVERE, "Error procesing SQS message", e);
    }
  }

  public void onMessage(Message message) {
    String trackingIdString = message.body();

    cargoInspectionService.inspectCargo(new TrackingId(trackingIdString));
  }
}
