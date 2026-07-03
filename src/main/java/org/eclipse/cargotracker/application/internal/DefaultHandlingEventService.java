package org.eclipse.cargotracker.application.internal;

import java.time.LocalDateTime;
import java.util.logging.Logger;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.application.HandlingEventService;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.CannotCreateHandlingEventException;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventFactory;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventRepository;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;

@Stateless
public class DefaultHandlingEventService implements HandlingEventService {

  @Inject private ApplicationEvents applicationEvents;
  @Inject private HandlingEventRepository handlingEventRepository;
  @Inject private HandlingEventFactory handlingEventFactory;
  @Inject private Logger logger;

  @Override
  public void registerHandlingEvent(
      LocalDateTime completionTime,
      TrackingId trackingId,
      VoyageNumber voyageNumber,
      UnLocode unLocode,
      HandlingEvent.Type type) throws CannotCreateHandlingEventException {

    LocalDateTime registrationTime = LocalDateTime.now(java.time.Clock.systemUTC());

    HandlingEvent event =
        handlingEventFactory.createHandlingEvent(
            registrationTime, completionTime, trackingId, voyageNumber, unLocode, type);

    handlingEventRepository.store(event);
    applicationEvents.cargoWasHandled(event);

    logger.info("Registered handling event");
  }
}
