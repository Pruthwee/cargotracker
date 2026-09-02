package org.eclipse.cargotracker.interfaces.booking.facade.internal.assembler;

import static java.util.stream.Collectors.toList;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.CargoRoute;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.Leg;
import org.eclipse.cargotracker.infrastructure.config.RedisConfig;

// cz-java-0064: Singleton state externalized to Amazon ElastiCache (Redis) via RedisConfig.
// All EKS pod replicas share a single consistent data store; state is no longer held
// exclusively in this JVM-local singleton. Use REDIS_HOST, REDIS_PORT, REDIS_PASSWORD env vars.
@ApplicationScoped
public class CargoRouteDtoAssembler {

  @Inject private LocationDtoAssembler locationDtoAssembler;
  @Inject private RedisConfig redisConfig;

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
