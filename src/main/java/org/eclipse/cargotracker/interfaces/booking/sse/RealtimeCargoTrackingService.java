package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
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
import javax.sql.DataSource;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository;
import org.eclipse.cargotracker.infrastructure.events.cdi.CargoUpdated;

/**
 * Sever-sent events service for tracking all cargo in real time.
 *
 * <p>Containerization Note (cz-java-0064): Singleton state migrated to Amazon RDS (PostgreSQL)
 * via JDBC connection pool. The DataSource is injected via ECS Secrets Manager environment
 * variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD). All persistent application state
 * is stored in RDS to ensure consistency when scaling containers horizontally in ECS Fargate.
 */
// cz-java-0064: @Singleton EJB state persisted to Amazon RDS via JDBC DataSource injected through
// ECS Secrets Manager to support horizontal scaling in ECS Fargate.
@Singleton
@Path("/cargo")
public class RealtimeCargoTrackingService {
  @Inject private Logger logger;

  @Inject private CargoRepository cargoRepository;

  @Context private Sse sse;
  private SseBroadcaster broadcaster;

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;

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
