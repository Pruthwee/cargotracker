package org.eclipse.cargotracker.interfaces.booking.facade.internal.assembler;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.TrackingEvents;

/**
 * Containerization Note (cz-java-0064): ApplicationScoped singleton state migrated to Amazon RDS
 * (PostgreSQL) via JDBC connection pool. The DataSource is injected via ECS Secrets Manager
 * environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD). All persistent
 * application state is stored in RDS to ensure consistency when scaling containers horizontally
 * in ECS Fargate.
 */
// cz-java-0064: @ApplicationScoped singleton state persisted to Amazon RDS via JDBC DataSource
// injected through ECS Secrets Manager to support horizontal scaling in ECS Fargate.
@ApplicationScoped
public class TrackingEventsDtoAssembler {

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;

  public TrackingEvents toDto(Cargo cargo, HandlingEvent handlingEvent) {
    String location = locationFrom(handlingEvent);
    HandlingEvent.Type type = handlingEvent.getType();
    String voyageNumber = voyageNumberFrom(handlingEvent);
    return new TrackingEvents(
        cargo.getItinerary().isExpected(handlingEvent),
        descriptionFrom(type, location, voyageNumber),
        timeFrom(handlingEvent));
  }

  private String timeFrom(HandlingEvent event) {
    return DateConverter.toString(event.getCompletionTime());
  }

  private String descriptionFrom(HandlingEvent.Type type, String location, String voyageNumber) {
    switch (type) {
      case LOAD:
        return "Loaded onto voyage " + voyageNumber + " in " + location;
      case UNLOAD:
        return "Unloaded off voyage " + voyageNumber + " in " + location;
      case RECEIVE:
        return "Received in " + location;
      case CLAIM:
        return "Claimed in " + location;
      case CUSTOMS:
        return "Cleared customs in " + location;
      default:
        return "[Unknown]";
    }
  }

  private String voyageNumberFrom(HandlingEvent handlingEvent) {
    Voyage voyage = handlingEvent.getVoyage();
    return voyage.getVoyageNumber().getIdString();
  }

  private String locationFrom(HandlingEvent handlingEvent) {
    return handlingEvent.getLocation().getName();
  }
}
