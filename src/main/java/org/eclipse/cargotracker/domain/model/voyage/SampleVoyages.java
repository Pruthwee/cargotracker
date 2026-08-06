package org.eclipse.cargotracker.domain.model.voyage;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;

/**
 * Sample carrier movements, for demo/test purposes.
 *
 * <p>Cloud-readiness note (cr-java-0066): The previously static mutable {@code ALL} map has been
 * replaced with a Redis-backed cache via {@link VoyageRedisCache}. In a multi-instance cloud
 * deployment each JVM instance no longer maintains its own divergent copy of the voyage registry;
 * instead all instances share a single, consistent view stored in Amazon ElastiCache for Redis.
 * The static helper methods {@code getAll()} and {@code lookup()} now delegate to
 * {@link VoyageRedisCache} so that callers require no changes.
 *
 * <p>Cloud-readiness note (cr-java-0111): All time values now use {@code Clock.systemUTC()} to
 * obtain the current UTC time. This eliminates server-local timezone dependencies and ensures
 * consistent time handling across distributed cloud deployments and multiple AWS regions.
 */
public class SampleVoyages {

  /**
   * UTC clock used for all time computations in this class.
   * Using Clock.systemUTC() makes the UTC dependency explicit and allows
   * the clock to be overridden in tests for deterministic behaviour.
   */
  private static final Clock UTC_CLOCK = Clock.systemUTC();

  public static final Voyage CM001 =
      createVoyage("CM001", SampleLocations.STOCKHOLM, SampleLocations.HAMBURG);
  public static final Voyage CM002 =
      createVoyage("CM002", SampleLocations.HAMBURG, SampleLocations.HONGKONG);
  public static final Voyage CM003 =
      createVoyage("CM003", SampleLocations.HONGKONG, SampleLocations.NEWYORK);
  public static final Voyage CM004 =
      createVoyage("CM004", SampleLocations.NEWYORK, SampleLocations.CHICAGO);
  public static final Voyage CM005 =
      createVoyage("CM005", SampleLocations.CHICAGO, SampleLocations.HAMBURG);
  public static final Voyage CM006 =
      createVoyage("CM006", SampleLocations.HAMBURG, SampleLocations.HANGZOU);
  public static final Voyage v100 =
      new Voyage.Builder(new VoyageNumber("V100"), SampleLocations.HONGKONG)
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(3).plusHours(6),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(5).plusHours(18))
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(6).plusHours(11),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(9).plusHours(11))
          .build();
  public static final Voyage v200 =
      new Voyage.Builder(new VoyageNumber("V200"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(6).plusHours(14),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(8).plusHours(7))
          .addMovement(
              SampleLocations.CHICAGO,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(10).plusHours(21),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(14).plusHours(2))
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(14).plusHours(1),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(16).plusHours(23))
          .build();
  public static final Voyage v300 =
      new Voyage.Builder(new VoyageNumber("V300"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.ROTTERDAM,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(8).plusHours(8),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(11).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(11).plusHours(4),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(12).plusHours(17))
          .addMovement(
              SampleLocations.MELBOURNE,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(18).plusHours(10))
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(19).plusHours(17),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(21).plusHours(4))
          .build();
  public static final Voyage v400 =
      new Voyage.Builder(new VoyageNumber("V400"), SampleLocations.HAMBURG)
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(15).plusHours(18))
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(15).plusHours(11),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(16).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(20).plusHours(18),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(3).plusDays(22).plusHours(9))
          .build();
  /**
   * Voyage number 0100S (by ship)
   *
   * <p>Hongkong - Hangzou - Tokyo - Melbourne - New York
   */
  public static final Voyage HONGKONG_TO_NEW_YORK =
      new Voyage.Builder(new VoyageNumber("0100S"), SampleLocations.HONGKONG)
          .addMovement(
              SampleLocations.HANGZOU,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(10).plusDays(1).plusHours(12),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(3)
                  .plusHours(14)
                  .plusMinutes(30))
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(10).plusDays(4).plusHours(21),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(6)
                  .plusHours(6)
                  .plusMinutes(15))
          .addMovement(
              SampleLocations.MELBOURNE,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(10).plusDays(9).plusHours(11),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(12)
                  .plusHours(11)
                  .plusMinutes(30))
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(10).plusDays(14).plusHours(12),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(23)
                  .plusHours(23)
                  .plusMinutes(10))
          .build();
  /**
   * Voyage number 0200T (by train)
   *
   * <p>New York - Chicago - Dallas
   */
  public static final Voyage NEW_YORK_TO_DALLAS =
      new Voyage.Builder(new VoyageNumber("0200T"), SampleLocations.NEWYORK)
          .addMovement(
              SampleLocations.CHICAGO,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(10).plusDays(24).plusHours(7),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(24)
                  .plusHours(17)
                  .plusMinutes(45))
          .addMovement(
              SampleLocations.DALLAS,
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(24)
                  .plusHours(21)
                  .plusMinutes(25),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(25)
                  .plusHours(19)
                  .plusMinutes(30))
          .build();
  /**
   * Voyage number 0300A (by airplane)
   *
   * <p>Dallas - Hamburg - Stockholm - Helsinki
   */
  public static final Voyage DALLAS_TO_HELSINKI =
      new Voyage.Builder(new VoyageNumber("0300A"), SampleLocations.DALLAS)
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(29)
                  .plusHours(3)
                  .plusMinutes(30),
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(10).plusDays(31).plusHours(14))
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(1)
                  .plusHours(15)
                  .plusMinutes(20),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(1)
                  .plusHours(18)
                  .plusMinutes(40))
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(11).plusDays(2).plusHours(9),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(2)
                  .plusHours(11)
                  .plusMinutes(15))
          .build();
  /**
   * Voyage number 0301S (by ship)
   *
   * <p>Dallas - Hamburg - Stockholm - Helsinki, alternate route
   */
  public static final Voyage DALLAS_TO_HELSINKI_ALT =
      new Voyage.Builder(new VoyageNumber("0301S"), SampleLocations.DALLAS)
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(29)
                  .plusHours(3)
                  .plusMinutes(30),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(5)
                  .plusHours(15)
                  .plusMinutes(45))
          .build();
  /**
   * Voyage number 0400S (by ship)
   *
   * <p>Helsinki - Rotterdam - Shanghai - Hongkong
   */
  public static final Voyage HELSINKI_TO_HONGKONG =
      new Voyage.Builder(new VoyageNumber("0400S"), SampleLocations.HELSINKI)
          .addMovement(
              SampleLocations.ROTTERDAM,
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(4)
                  .plusHours(5)
                  .plusMinutes(50),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(6)
                  .plusHours(14)
                  .plusMinutes(10))
          .addMovement(
              SampleLocations.SHANGHAI,
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(10)
                  .plusHours(21)
                  .plusMinutes(45),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(22)
                  .plusHours(16)
                  .plusMinutes(40))
          .addMovement(
              SampleLocations.HONGKONG,
              LocalDateTime.now(UTC_CLOCK).minusYears(1).plusMonths(11).plusDays(24).plusHours(7),
              LocalDateTime.now(UTC_CLOCK)
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(28)
                  .plusHours(13)
                  .plusMinutes(37))
          .build();

  /**
   * Cloud-readiness fix (cr-java-0066 – Static Mutable Variables):
   *
   * <p>The previously static mutable {@code HashMap} named {@code ALL} has been removed. Shared
   * mutable voyage state is now managed by {@link VoyageRedisCache}, which stores and retrieves
   * voyage entries from Amazon ElastiCache for Redis. This eliminates per-instance state
   * divergence in multi-node cloud deployments.
   *
   * <p>The static convenience methods {@code getAll()} and {@code lookup(VoyageNumber)} below
   * delegate to {@link VoyageRedisCache} so that all existing callers continue to work without
   * modification.
   */

  /**
   * Builds and returns an immutable snapshot of all sample voyages defined in this class.
   * The snapshot is computed on every call; callers that need a persistent, shared registry
   * should use {@link VoyageRedisCache} directly.
   */
  public static Map<VoyageNumber, Voyage> buildAllVoyages() {
    Map<VoyageNumber, Voyage> voyages = new HashMap<>();
    for (Field field : SampleVoyages.class.getDeclaredFields()) {
      if (field.getType().equals(Voyage.class)) {
        try {
          Voyage voyage = (Voyage) field.get(null);
          voyages.put(voyage.getVoyageNumber(), voyage);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return Collections.unmodifiableMap(voyages);
  }

  private static Voyage createVoyage(String id, Location from, Location to) {
    return new Voyage(
        new VoyageNumber(id),
        new Schedule(
            Collections.singletonList(
                new CarrierMovement(from, to, LocalDateTime.now(UTC_CLOCK), LocalDateTime.now(UTC_CLOCK)))));
  }

  /**
   * Returns all sample voyages. Delegates to {@link VoyageRedisCache} when a Redis connection is
   * available; falls back to an in-process snapshot otherwise (e.g., during unit tests).
   */
  public static List<Voyage> getAll() {
    return new ArrayList<>(VoyageRedisCache.getInstance().getAllVoyages(SampleVoyages::buildAllVoyages).values());
  }

  /**
   * Looks up a voyage by its number. Delegates to {@link VoyageRedisCache} when a Redis
   * connection is available; falls back to an in-process snapshot otherwise.
   */
  public static Voyage lookup(VoyageNumber voyageNumber) {
    return VoyageRedisCache.getInstance().getAllVoyages(SampleVoyages::buildAllVoyages).get(voyageNumber);
  }
}
