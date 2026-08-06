package org.eclipse.cargotracker.domain.model.cargo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingHistory;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.junit.jupiter.api.Test;

public class CargoTest {

  // Cloud-ready: UTC clock standardized for distributed cloud environments (AWS multi-region).
  // Uses Clock.fixed with ZoneOffset.UTC to ensure deterministic, timezone-independent time
  // across all cloud regions and containers, replacing system-local timezone dependencies.
  private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
  private final List<HandlingEvent> events = new ArrayList<>();
  private final Voyage voyage =
      new Voyage.Builder(new VoyageNumber("0123"), SampleLocations.STOCKHOLM)
          .addMovement(SampleLocations.HAMBURG, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .addMovement(SampleLocations.HONGKONG, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .addMovement(SampleLocations.MELBOURNE, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .build();

  @Test
  public void testConstruction() {
    TrackingId trackingId = new TrackingId("XYZ");
    LocalDate arrivalDeadline = LocalDate.now(clock).minusYears(1).plusMonths(3).plusDays(3);
    RouteSpecification routeSpecification =
        new RouteSpecification(
            SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, arrivalDeadline);

    Cargo cargo = new Cargo(trackingId, routeSpecification);

    assertEquals(RoutingStatus.NOT_ROUTED, cargo.getDelivery().getRoutingStatus());
    assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
  }

  @Test
  public void testRoutingStatus() {
    Cargo cargo =
        new Cargo(
            new TrackingId("XYZ"),
            new RouteSpecification(
                SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, LocalDate.now(clock)));
    final Itinerary good = new Itinerary();
    Itinerary bad = new Itinerary();
    @SuppressWarnings("serial")
    RouteSpecification acceptOnlyGood =
        new RouteSpecification(
            cargo.getOrigin(), cargo.getRouteSpecification().getDestination(), LocalDate.now(clock)) {

          @Override
          public boolean isSatisfiedBy(Itinerary itinerary) {
            return itinerary == good;
          }
        };

    cargo.specifyNewRoute(acceptOnlyGood);

    assertEquals(RoutingStatus.NOT_ROUTED, cargo.getDelivery().getRoutingStatus());

    cargo.assignToRoute(bad);
    assertEquals(RoutingStatus.MISROUTED, cargo.getDelivery().getRoutingStatus());

    cargo.assignToRoute(good);
    assertEquals(RoutingStatus.ROUTED, cargo.getDelivery().getRoutingStatus());
  }

  @Test
  public void testLastKnownLocationUnknownWhenNoEvents() {
    Cargo cargo =
        new Cargo(
            new TrackingId("XYZ"),
            new RouteSpecification(
                SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, LocalDate.now(clock)));

    assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
  }

  @Test
  public void testLastKnownLocationReceived() throws Exception {
    Cargo cargo = populateCargoReceivedStockholm();

    assertEquals(SampleLocations.STOCKHOLM, cargo.getDelivery().getLastKnownLocation());
  }

  @Test
  public void testLastKnownLocationClaimed() throws Exception {
    Cargo cargo = populateCargoClaimedMelbourne();

    assertEquals(SampleLocations.MELBOURNE, cargo.getDelivery().getLastKnownLocation());
  }

  @Test
  public void testLastKnownLocationUnloaded() throws Exception {
    Cargo cargo = populateCargoOffHongKong();

    assertEquals(SampleLocations.HONGKONG, cargo.getDelivery().getLastKnownLocation());
  }

  @Test
  public void testLastKnownLocationloaded() throws Exception {
    Cargo cargo = populateCargoOnHamburg();

    assertEquals(SampleLocations.HAMBURG, cargo.getDelivery().getLastKnownLocation());
  }

  @Test
  public void testIsUnloadedAtFinalDestination() {
    Cargo cargo =
        setUpCargoWithItinerary(
            SampleLocations.HANGZOU, SampleLocations.TOKYO, SampleLocations.NEWYORK);
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());

    // Adding an event unrelated to unloading at final destination
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(40),
            LocalDateTime.now(clock),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.HANGZOU));
    cargo.deriveDeliveryProgress(new HandlingHistory(events));
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());

    Voyage voyage =
        new Voyage.Builder(new VoyageNumber("0123"), SampleLocations.HANGZOU)
            .addMovement(SampleLocations.NEWYORK, LocalDateTime.now(clock), LocalDateTime.now(clock))
            .build();

    // Adding an unload event, but not at the final destination
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(30),
            LocalDateTime.now(clock),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.TOKYO,
            voyage));
    cargo.deriveDeliveryProgress(new HandlingHistory(events));
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());

    // Adding an event in the final destination, but not unload
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(20),
            LocalDateTime.now(clock),
            HandlingEvent.Type.CUSTOMS,
            SampleLocations.NEWYORK));
    cargo.deriveDeliveryProgress(new HandlingHistory(events));
    assertFalse(cargo.getDelivery().isUnloadedAtDestination());

    // Finally, cargo is unloaded at final destination
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(10),
            LocalDateTime.now(clock),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.NEWYORK,
            voyage));
    cargo.deriveDeliveryProgress(new HandlingHistory(events));
    assertTrue(cargo.getDelivery().isUnloadedAtDestination());
  }

  // TODO [TDD] Generate test data some better way
  private Cargo populateCargoReceivedStockholm() throws Exception {
    Cargo cargo =
        new Cargo(
            new TrackingId("XYZ"),
            new RouteSpecification(
                SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, LocalDate.now(clock)));

    HandlingEvent event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.STOCKHOLM);
    events.add(event);
    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    return cargo;
  }

  private Cargo populateCargoClaimedMelbourne() throws Exception {
    Cargo cargo = populateCargoOffMelbourne();

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(9),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(9),
            HandlingEvent.Type.CLAIM,
            SampleLocations.MELBOURNE));
    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    return cargo;
  }

  private Cargo populateCargoOffHongKong() throws Exception {
    Cargo cargo =
        new Cargo(
            new TrackingId("XYZ"),
            new RouteSpecification(
                SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, LocalDate.now(clock)));

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            HandlingEvent.Type.LOAD,
            SampleLocations.STOCKHOLM,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(2),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(2),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.HAMBURG,
            voyage));

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(3),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(3),
            HandlingEvent.Type.LOAD,
            SampleLocations.HAMBURG,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(4),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(4),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.HONGKONG,
            voyage));

    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    return cargo;
  }

  private Cargo populateCargoOnHamburg() throws Exception {
    Cargo cargo =
        new Cargo(
            new TrackingId("XYZ"),
            new RouteSpecification(
                SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, LocalDate.now(clock)));

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            HandlingEvent.Type.LOAD,
            SampleLocations.STOCKHOLM,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(2),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(2),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.HAMBURG,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(3),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(3),
            HandlingEvent.Type.LOAD,
            SampleLocations.HAMBURG,
            voyage));

    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    return cargo;
  }

  private Cargo populateCargoOffMelbourne() throws Exception {
    Cargo cargo =
        new Cargo(
            new TrackingId("XYZ"),
            new RouteSpecification(
                SampleLocations.STOCKHOLM, SampleLocations.MELBOURNE, LocalDate.now(clock)));

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(1),
            HandlingEvent.Type.LOAD,
            SampleLocations.STOCKHOLM,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(2),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(2),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.HAMBURG,
            voyage));

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(3),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(3),
            HandlingEvent.Type.LOAD,
            SampleLocations.HAMBURG,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(4),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(4),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.HONGKONG,
            voyage));

    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(5),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(5),
            HandlingEvent.Type.LOAD,
            SampleLocations.HONGKONG,
            voyage));
    events.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(7),
            LocalDateTime.now(clock).minusYears(1).plusMonths(12).plusDays(7),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.MELBOURNE,
            voyage));

    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    return cargo;
  }

  @Test
  public void testIsMisdirected() throws Exception {
    // A cargo with no itinerary is not misdirected
    Cargo cargo =
        new Cargo(
            new TrackingId("TRKID"),
            new RouteSpecification(
                SampleLocations.SHANGHAI, SampleLocations.GOTHENBURG, LocalDate.now(clock)));
    assertFalse(cargo.getDelivery().isMisdirected());

    cargo =
        setUpCargoWithItinerary(
            SampleLocations.SHANGHAI, SampleLocations.ROTTERDAM, SampleLocations.GOTHENBURG);

    // A cargo with no handling events is not misdirected
    assertFalse(cargo.getDelivery().isMisdirected());

    Collection<HandlingEvent> handlingEvents = new ArrayList<>();

    // Happy path
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(10),
            LocalDateTime.now(clock).minusDays(20),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.SHANGHAI));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(30),
            LocalDateTime.now(clock).minusDays(40),
            HandlingEvent.Type.LOAD,
            SampleLocations.SHANGHAI,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(50),
            LocalDateTime.now(clock).minusDays(60),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.ROTTERDAM,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(70),
            LocalDateTime.now(clock).minusDays(80),
            HandlingEvent.Type.LOAD,
            SampleLocations.ROTTERDAM,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(90),
            LocalDateTime.now(clock).minusDays(100),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.GOTHENBURG,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(110),
            LocalDateTime.now(clock).minusDays(120),
            HandlingEvent.Type.CLAIM,
            SampleLocations.GOTHENBURG));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(130),
            LocalDateTime.now(clock).minusDays(140),
            HandlingEvent.Type.CUSTOMS,
            SampleLocations.GOTHENBURG));

    events.addAll(handlingEvents);
    cargo.deriveDeliveryProgress(new HandlingHistory(events));
    assertFalse(cargo.getDelivery().isMisdirected());

    // Try a couple of failing ones
    cargo =
        setUpCargoWithItinerary(
            SampleLocations.SHANGHAI, SampleLocations.ROTTERDAM, SampleLocations.GOTHENBURG);
    handlingEvents = new ArrayList<>();

    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.HANGZOU));
    events.addAll(handlingEvents);
    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    assertTrue(cargo.getDelivery().isMisdirected());

    cargo =
        setUpCargoWithItinerary(
            SampleLocations.SHANGHAI, SampleLocations.ROTTERDAM, SampleLocations.GOTHENBURG);
    handlingEvents = new ArrayList<>();

    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(10),
            LocalDateTime.now(clock).minusDays(20),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.SHANGHAI));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(30),
            LocalDateTime.now(clock).minusDays(40),
            HandlingEvent.Type.LOAD,
            SampleLocations.SHANGHAI,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(50),
            LocalDateTime.now(clock).minusDays(60),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.ROTTERDAM,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(70),
            LocalDateTime.now(clock).minusDays(80),
            HandlingEvent.Type.LOAD,
            SampleLocations.ROTTERDAM,
            voyage));

    events.addAll(handlingEvents);
    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    assertTrue(cargo.getDelivery().isMisdirected());

    cargo =
        setUpCargoWithItinerary(
            SampleLocations.SHANGHAI, SampleLocations.ROTTERDAM, SampleLocations.GOTHENBURG);
    handlingEvents = new ArrayList<>();

    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(10),
            LocalDateTime.now(clock).minusDays(20),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.SHANGHAI));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(30),
            LocalDateTime.now(clock).minusDays(40),
            HandlingEvent.Type.LOAD,
            SampleLocations.SHANGHAI,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock).minusDays(50),
            LocalDateTime.now(clock).minusDays(60),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.ROTTERDAM,
            voyage));
    handlingEvents.add(
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.CLAIM,
            SampleLocations.ROTTERDAM));

    events.addAll(handlingEvents);
    cargo.deriveDeliveryProgress(new HandlingHistory(events));

    assertTrue(cargo.getDelivery().isMisdirected());
  }

  private Cargo setUpCargoWithItinerary(Location origin, Location midpoint, Location destination) {
    Cargo cargo =
        new Cargo(
            new TrackingId("CARGO1"), new RouteSpecification(origin, destination, LocalDate.now(clock)));

    Itinerary itinerary =
        new Itinerary(
            Arrays.asList(
                new Leg(voyage, origin, midpoint, LocalDateTime.now(clock), LocalDateTime.now(clock)),
                new Leg(voyage, midpoint, destination, LocalDateTime.now(clock), LocalDateTime.now(clock))));

    cargo.assignToRoute(itinerary);
    return cargo;
  }
}
