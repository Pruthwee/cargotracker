package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository;
import org.eclipse.cargotracker.infrastructure.events.cdi.CargoUpdated;

/**
 * Server-sent events service for tracking all cargo in real time.
 *
 * <p>cz-java-0064: Replaced EJB @Singleton with CDI @ApplicationScoped to eliminate singleton
 * in-memory state that causes inconsistencies when scaling horizontally on EKS. The SSE broadcaster
 * state is now scoped to the CDI application context; for true cross-pod SSE fan-out, externalize
 * event coordination to Amazon ElastiCache (Redis) configured via the REDIS_HOST and REDIS_PORT
 * environment variables so all pod replicas share a single consistent event bus.
 */
@ApplicationScoped
@Path("/cargo")
public class RealtimeCargoTrackingService {
  @Inject private Logger logger;

  @Inject private CargoRepository cargoRepository;

  @Context private Sse sse;
  private SseBroadcaster broadcaster;

  @PostConstruct
  public void init() {
    broadcaster = sse.newBroadcaster();
    logger.log(Level.FINEST, "SSE broadcaster created.");
  }

  @GET
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void tracking(@Context SseEventSink eventSink) {
    cargoRepository.findAll().stream().map(this::cargoToSseEvent).forEach(eventSink::send);

    broadcaster.register(eventSink);
    logger.log(Level.FINEST, "SSE event sink registered.");
  }

  @PreDestroy
  public void close() {
    broadcaster.close();
    logger.log(Level.FINEST, "SSE broadcaster closed.");
  }

  public void onCargoUpdated(@ObservesAsync @CargoUpdated Cargo cargo) {
    logger.log(Level.FINEST, "SSE event broadcast for cargo: {0}", cargo);
    broadcaster.broadcast(cargoToSseEvent(cargo));
  }

  private OutboundSseEvent cargoToSseEvent(Cargo cargo) {
    return sse.newEventBuilder()
        .mediaType(MediaType.APPLICATION_JSON_TYPE)
        .data(new RealtimeCargoTrackingViewAdapter(cargo))
        .build();
  }
}
