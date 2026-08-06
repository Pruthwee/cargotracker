package org.eclipse.cargotracker.domain.model.cargo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.junit.jupiter.api.Test;

public class ItineraryTest {

  // Cloud-ready: UTC clock standardized for distributed cloud environments (AWS multi-region).
  // Uses Clock.fixed with ZoneOffset.UTC to ensure deterministic, timezone-independent time
  // across all cloud regions and containers, replacing system-local timezone dependencies.
  private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

  private Voyage voyage =
      new Voyage.Builder(new VoyageNumber("0123"), SampleLocations.SHANGHAI)
          .addMovement(SampleLocations.ROTTERDAM, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .addMovement(SampleLocations.GOTHENBURG, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .build();
  private Voyage wrongVoyage =
      new Voyage.Builder(new VoyageNumber("666"), SampleLocations.NEWYORK)
          .addMovement(SampleLocations.STOCKHOLM, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .addMovement(SampleLocations.HELSINKI, LocalDateTime.now(clock), LocalDateTime.now(clock))
          .build();

  @Test
  public void testCargoOnTrack() {
    TrackingId trackingId = new TrackingId("CARGO1");
    RouteSpecification routeSpecification =
        new RouteSpecification(
            SampleLocations.SHANGHAI, SampleLocations.GOTHENBURG, LocalDate.now(clock));
    Cargo cargo = new Cargo(trackingId, routeSpecification);

    Itinerary itinerary =
        new Itinerary(
            Arrays.asList(
                new Leg(
                    voyage,
                    SampleLocations.SHANGHAI,
                    SampleLocations.ROTTERDAM,
                    LocalDateTime.now(clock),
                    LocalDateTime.now(clock)),
                new Leg(
                    voyage,
                    SampleLocations.ROTTERDAM,
                    SampleLocations.GOTHENBURG,
                    LocalDateTime.now(clock),
                    LocalDateTime.now(clock))));

    // Happy path
    HandlingEvent event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.SHANGHAI);
    assertTrue(itinerary.isExpected(event));

    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.LOAD,
            SampleLocations.SHANGHAI,
            voyage);
    assertTrue(itinerary.isExpected(event));

    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.ROTTERDAM,
            voyage);
    assertTrue(itinerary.isExpected(event));

    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.LOAD,
            SampleLocations.ROTTERDAM,
            voyage);
    assertTrue(itinerary.isExpected(event));

    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.GOTHENBURG,
            voyage);
    assertTrue(itinerary.isExpected(event));

    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.CLAIM,
            SampleLocations.GOTHENBURG);
    assertTrue(itinerary.isExpected(event));

    // Customs event changes nothing
    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.CUSTOMS,
            SampleLocations.GOTHENBURG);
    assertTrue(itinerary.isExpected(event));

    // Received at the wrong location
    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.HANGZOU);
    assertFalse(itinerary.isExpected(event));

    // Loaded to onto the wrong ship, correct location
    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.LOAD,
            SampleLocations.ROTTERDAM,
            wrongVoyage);
    assertFalse(itinerary.isExpected(event));

    // Unloaded from the wrong ship in the wrong location
    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.HELSINKI,
            wrongVoyage);
    assertFalse(itinerary.isExpected(event));

    event =
        new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.CLAIM,
            SampleLocations.ROTTERDAM);
    assertFalse(itinerary.isExpected(event));
  }

  @Test
  public void testNextExpectedEvent() {
    TrackingId trackingId = new TrackingId("CARGO1");
    RouteSpecification routeSpecification =
            new RouteSpecification(
                    SampleLocations.SHANGHAI, SampleLocations.GOTHENBURG, LocalDate.now(clock));
    Cargo cargo = new Cargo(trackingId, routeSpecification);

    Itinerary itinerary = new Itinerary(Arrays.asList(
            new Leg(
                    voyage,
                    SampleLocations.SHANGHAI,
                    SampleLocations.ROTTERDAM,
                    LocalDateTime.now(clock),
                    LocalDateTime.now(clock)),
            new Leg(
                    voyage,
                    SampleLocations.ROTTERDAM,
                    SampleLocations.GOTHENBURG,
                    LocalDateTime.now(clock),
                    LocalDateTime.now(clock))));

    HandlingEvent receiveEvent = new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.RECEIVE,
            SampleLocations.SHANGHAI);
    assertTrue(itinerary.isExpected(receiveEvent));

    HandlingEvent unexpectedEvent = new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.UNLOAD,
            SampleLocations.ROTTERDAM,
            wrongVoyage);
    assertFalse(itinerary.isExpected(unexpectedEvent));

    HandlingEvent claimEvent = new HandlingEvent(
            cargo,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock),
            HandlingEvent.Type.CLAIM,
            SampleLocations.GOTHENBURG);
    assertTrue(itinerary.isExpected(claimEvent));
  }

  @Test
  public void testCreateItinerary() {
    try {
      @SuppressWarnings("unused")
      Itinerary itinerary = new Itinerary(new ArrayList<>());
      fail("An empty itinerary is not OK");
    } catch (IllegalArgumentException iae) {
      // Expected
    }

    try {
      List<Leg> legs = null;
      @SuppressWarnings("unused")
      Itinerary itinerary = new Itinerary(legs);
      fail("Null itinerary is not OK");
    } catch (NullPointerException npe) {
      // Expected
    }
  }
}
