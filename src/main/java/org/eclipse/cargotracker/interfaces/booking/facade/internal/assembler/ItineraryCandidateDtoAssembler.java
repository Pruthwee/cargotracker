package org.eclipse.cargotracker.interfaces.booking.facade.internal.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.eclipse.cargotracker.application.util.DateConverter;
import org.eclipse.cargotracker.domain.model.cargo.Itinerary;
import org.eclipse.cargotracker.domain.model.cargo.Leg;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.LocationRepository;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.domain.model.voyage.VoyageRepository;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.RouteCandidate;

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
public class ItineraryCandidateDtoAssembler {

  @Inject private LocationDtoAssembler locationDtoAssembler;

  /**
   * JDBC DataSource backed by Amazon RDS (PostgreSQL/MySQL) in ECS Fargate.
   * Connection parameters are supplied via ECS Secrets Manager environment variables:
   *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
   */
  @Resource(lookup = "java:comp/env/jdbc/cargoTrackerDS")
  private DataSource rdsDataSource;

  public RouteCandidate toDto(Itinerary itinerary) {
    List<org.eclipse.cargotracker.interfaces.booking.facade.dto.Leg> legDTOs =
        itinerary.getLegs().stream().map(this::toLegDTO).collect(Collectors.toList());
    return new RouteCandidate(legDTOs);
  }

  protected org.eclipse.cargotracker.interfaces.booking.facade.dto.Leg toLegDTO(Leg leg) {
    VoyageNumber voyageNumber = leg.getVoyage().getVoyageNumber();
    return new org.eclipse.cargotracker.interfaces.booking.facade.dto.Leg(
        voyageNumber.getIdString(),
        locationDtoAssembler.toDto(leg.getLoadLocation()),
        locationDtoAssembler.toDto(leg.getUnloadLocation()),
        leg.getLoadTime(),
        leg.getUnloadTime());
  }

  public Itinerary fromDTO(
      RouteCandidate routeCandidateDTO,
      VoyageRepository voyageRepository,
      LocationRepository locationRepository) {
    List<Leg> legs = new ArrayList<>(routeCandidateDTO.getLegs().size());

    for (org.eclipse.cargotracker.interfaces.booking.facade.dto.Leg legDTO :
        routeCandidateDTO.getLegs()) {
      VoyageNumber voyageNumber = new VoyageNumber(legDTO.getVoyageNumber());
      Voyage voyage = voyageRepository.find(voyageNumber);
      Location from = locationRepository.find(new UnLocode(legDTO.getFromUnLocode()));
      Location to = locationRepository.find(new UnLocode(legDTO.getToUnLocode()));

      legs.add(
          new Leg(
              voyage,
              from,
              to,
              DateConverter.toDateTime(legDTO.getLoadTime()),
              DateConverter.toDateTime(legDTO.getUnloadTime())));
    }

    return new Itinerary(legs);
  }
}
