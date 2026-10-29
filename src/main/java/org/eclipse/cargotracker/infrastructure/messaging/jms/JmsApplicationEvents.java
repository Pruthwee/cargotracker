package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.infrastructure.messaging.sqs.SqsQueue;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.core.exception.SdkException;

@ApplicationScoped
public class JmsApplicationEvents implements ApplicationEvents, Serializable {

  private static final long serialVersionUID = 1L;
  private static final int LOW_PRIORITY = 0;
  @Inject JMSContext jmsContext;

  @Resource(lookup = "java:app/jms/MisdirectedCargoQueue")
  private Destination misdirectedCargoQueue;

  @Resource(lookup = "java:app/jms/DeliveredCargoQueue")
  private Destination deliveredCargoQueue;

  @Resource(lookup = "java:app/jms/HandlingEventRegistrationAttemptQueue")
  private Destination handlingEventQueue;

  @Inject private Logger logger;

  @Inject private SqsQueue sqsQueue;

  @Override
  public void cargoWasHandled(HandlingEvent event) {
    Cargo cargo = event.getCargo();
    logger.log(Level.INFO, "Cargo was handled {0}", cargo);
    String queueUrl = System.getenv(SqsQueue.CARGO_HANDLED_QUEUE_URL_ENV);

    try {
      if (queueUrl == null || queueUrl.trim().isEmpty()) {
        throw new IOException(SqsQueue.CARGO_HANDLED_QUEUE_URL_ENV + " is not set");
      }

      sqsQueue.send(queueUrl.trim(), cargo.getTrackingId().getIdString());
    } catch (IOException | SdkException e) {
      logger.log(Level.SEVERE, "Error sending cargo handled message to SQS", e);
    }
  }

  @Override
  public void cargoWasMisdirected(Cargo cargo) {
    logger.log(Level.INFO, "Cargo was misdirected {0}", cargo);
    jmsContext
        .createProducer()
        .setPriority(LOW_PRIORITY)
        .setDisableMessageID(true)
        .setDisableMessageTimestamp(true)
        .send(misdirectedCargoQueue, cargo.getTrackingId().getIdString());
  }

  @Override
  public void cargoHasArrived(Cargo cargo) {
    logger.log(Level.INFO, "Cargo has arrived {0}", cargo);
    jmsContext
        .createProducer()
        .setPriority(LOW_PRIORITY)
        .setDisableMessageID(true)
        .setDisableMessageTimestamp(true)
        .send(deliveredCargoQueue, cargo.getTrackingId().getIdString());
  }

  @Override
  public void receivedHandlingEventRegistrationAttempt(HandlingEventRegistrationAttempt attempt) {
    logger.log(Level.INFO, "Received handling event registration attempt {0}", attempt);
    jmsContext
        .createProducer()
        .setPriority(LOW_PRIORITY)
        .setDisableMessageID(true)
        .setDisableMessageTimestamp(true)
        .setTimeToLive(1000)
        .send(handlingEventQueue, attempt);
  }
}
