package org.eclipse.cargotracker.infrastructure.messaging.jms;

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
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ApplicationScoped
public class JmsApplicationEvents implements ApplicationEvents, Serializable {

  private static final long serialVersionUID = 1L;
  private static final int LOW_PRIORITY = 0;
  @Inject JMSContext jmsContext;

  @Resource(lookup = "java:app/jms/CargoHandledQueue")
  private Destination cargoHandledQueue;

  @Resource(lookup = "java:app/jms/MisdirectedCargoQueue")
  private Destination misdirectedCargoQueue;

  @Resource(lookup = "java:app/jms/DeliveredCargoQueue")
  private Destination deliveredCargoQueue;

  @Resource(lookup = "java:app/jms/HandlingEventRegistrationAttemptQueue")
  private Destination handlingEventQueue;

  @Inject private Logger logger;

  private SqsClient sqsClient;
  private String cargoHandledQueueUrl;
  private final Object initLock = new Object();

  /** Lazily initializes the SQS client and queue URL from environment variables on first use. */
  private void ensureSqsInitialized() {
    if (sqsClient != null) {
      return;
    }
    synchronized (initLock) {
      if (sqsClient != null) {
        return;
      }
      cargoHandledQueueUrl = System.getenv("SQS_CARGO_HANDLED_QUEUE_URL");
      if (cargoHandledQueueUrl == null || cargoHandledQueueUrl.isEmpty()) {
        logger.log(Level.WARNING, "SQS_CARGO_HANDLED_QUEUE_URL not set, SQS messaging disabled");
        return;
      }
      try {
        sqsClient = SqsClient.builder().build();
      } catch (SdkException e) {
        logger.log(Level.WARNING, "Failed to create SQS client, SQS messaging disabled", e);
      }
    }
  }

  @Override
  public void cargoWasHandled(HandlingEvent event) {
    Cargo cargo = event.getCargo();
    logger.log(Level.INFO, "Cargo was handled {0}", cargo);

    ensureSqsInitialized();
    if (sqsClient != null && cargoHandledQueueUrl != null) {
      try {
        SendMessageRequest request =
            SendMessageRequest.builder()
                .queueUrl(cargoHandledQueueUrl)
                .messageBody(cargo.getTrackingId().getIdString())
                .build();
        sqsClient.sendMessage(request);
      } catch (SdkException e) {
        logger.log(Level.SEVERE, "Failed to send SQS message for cargo handled", e);
      }
    } else {
      // Fallback to JMS if SQS is not configured
      logger.log(Level.WARNING, "SQS not configured, falling back to JMS for cargo handled event");
      jmsContext
          .createProducer()
          .setPriority(LOW_PRIORITY)
          .setDisableMessageID(true)
          .setDisableMessageTimestamp(true)
          .send(cargoHandledQueue, cargo.getTrackingId().getIdString());
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
