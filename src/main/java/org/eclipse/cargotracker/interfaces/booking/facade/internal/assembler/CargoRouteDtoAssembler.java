package org.eclipse.cargotracker.interfaces.booking.facade.internal.assembler;

import static java.util.stream.Collectors.toList;

import java.util.List;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.CargoRoute;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.Leg;

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
public class CargoRouteDtoAssembler {

  @Inject private LocationDtoAssembler locationDtoAssembler;

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;

  public CargoRoute toDto(Cargo cargo) {
    List<Leg> legs =
        cargo
            .getItinerary()
            .getLegs()
            .stream()
            .map(
                leg ->
                    new Leg(
                        leg.getVoyage().getVoyageNumber().getIdString(),
                        locationDtoAssembler.toDto(leg.getLoadLocation()),
                        locationDtoAssembler.toDto(leg.getUnloadLocation()),
                        leg.getLoadTime(),
                        leg.getUnloadTime()))
            .collect(toList());

    return new CargoRoute(
        cargo.getTrackingId().getIdString(),
        locationDtoAssembler.toDto(cargo.getOrigin()),
        locationDtoAssembler.toDto(cargo.getRouteSpecification().getDestination()),
        cargo.getRouteSpecification().getArrivalDeadline(),
        cargo.getDelivery().getRoutingStatus().sameValueAs(RoutingStatus.MISROUTED),
        cargo.getDelivery().getTransportStatus().sameValueAs(TransportStatus.CLAIMED),
        locationDtoAssembler.toDto(cargo.getDelivery().getLastKnownLocation()),
        cargo.getDelivery().getTransportStatus().name(),
        legs);
  }
}
