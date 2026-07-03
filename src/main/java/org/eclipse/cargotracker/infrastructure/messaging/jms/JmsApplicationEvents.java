package org.eclipse.cargotracker.infrastructure.messaging.jms;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Amazon SQS-based application events implementation.
 * Replaces JMS queue abstractions with native Amazon SQS integration to eliminate
 * dependency on traditional message brokers and leverage cloud-native, fully managed queuing.
 */
@ApplicationScoped
public class JmsApplicationEvents implements ApplicationEvents, Serializable {

  private static final long serialVersionUID = 1L;

  private static final String CARGO_HANDLED_QUEUE_URL_ENV = "CARGO_HANDLED_QUEUE_URL";
  private static final String MISDIRECTED_CARGO_QUEUE_URL_ENV = "MISDIRECTED_CARGO_QUEUE_URL";
  private static final String DELIVERED_CARGO_QUEUE_URL_ENV = "DELIVERED_CARGO_QUEUE_URL";
  private static final String HANDLING_EVENT_QUEUE_URL_ENV = "HANDLING_EVENT_QUEUE_URL";

  @Inject private Logger logger;

  @Override
  public void cargoWasHandled(HandlingEvent event) {
    Cargo cargo = event.getCargo();
    logger.log(Level.INFO, "Cargo was handled {0}", cargo);
    sendSqsMessage(CARGO_HANDLED_QUEUE_URL_ENV, cargo.getTrackingId().getIdString());
  }

  @Override
  public void cargoWasMisdirected(Cargo cargo) {
    logger.log(Level.INFO, "Cargo was misdirected {0}", cargo);
    sendSqsMessage(MISDIRECTED_CARGO_QUEUE_URL_ENV, cargo.getTrackingId().getIdString());
  }

  @Override
  public void cargoHasArrived(Cargo cargo) {
    logger.log(Level.INFO, "Cargo has arrived {0}", cargo);
    sendSqsMessage(DELIVERED_CARGO_QUEUE_URL_ENV, cargo.getTrackingId().getIdString());
  }

  @Override
  public void receivedHandlingEventRegistrationAttempt(HandlingEventRegistrationAttempt attempt) {
    logger.log(Level.INFO, "Received handling event registration attempt {0}", attempt);
    // Serialize the attempt as a JSON-like string for SQS message body
    String messageBody = serializeAttempt(attempt);
    sendSqsMessage(HANDLING_EVENT_QUEUE_URL_ENV, messageBody);
  }

  private void sendSqsMessage(String queueUrlEnvVar, String messageBody) {
    String queueUrl = System.getenv(queueUrlEnvVar);
    if (queueUrl == null || queueUrl.isEmpty()) {
      logger.log(Level.WARNING, "SQS queue URL not configured via environment variable: {0}",
          queueUrlEnvVar);
      return;
    }

    try (SqsClient sqsClient = SqsClient.builder().build()) {
      SendMessageRequest sendRequest = SendMessageRequest.builder()
          .queueUrl(queueUrl)
          .messageBody(messageBody)
          .build();
      sqsClient.sendMessage(sendRequest);
      logger.log(Level.FINE, "Sent SQS message to queue: {0}", queueUrlEnvVar);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error sending SQS message to queue " + queueUrlEnvVar, e);
    }
  }

  private String serializeAttempt(HandlingEventRegistrationAttempt attempt) {
    // Simple serialization for SQS message body
    return attempt.getTrackingId().getIdString()
        + "|" + (attempt.getVoyageNumber() != null ? attempt.getVoyageNumber().getIdString() : "")
        + "|" + attempt.getUnLocode().getIdString()
        + "|" + attempt.getType().name()
        + "|" + attempt.getCompletionTime().toString()
        + "|" + attempt.getRegistrationTime().toString();
  }
}
