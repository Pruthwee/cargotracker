package org.eclipse.cargotracker.domain.model.voyage;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;

/**
 * Sample carrier movements, for demo/test purposes.
 * Uses UTC-based time (via Instant and ZoneOffset.UTC) for cloud-native time handling
 * across distributed environments.
 */
public class SampleVoyages {

  // Helper method to get current UTC time as LocalDateTime for cloud-native consistency
  private static LocalDateTime utcNow() {
    return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
  }

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
              utcNow().minusYears(1).plusMonths(3).plusDays(3).plusHours(6),
              utcNow().minusYears(1).plusMonths(3).plusDays(5).plusHours(18))
          .addMovement(
              SampleLocations.NEWYORK,
              utcNow().minusYears(1).plusMonths(3).plusDays(6).plusHours(11),
              utcNow().minusYears(1).plusMonths(3).plusDays(9).plusHours(11))
          .build();
  public static final Voyage v200 =
      new Voyage.Builder(new VoyageNumber("V200"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.NEWYORK,
              utcNow().minusYears(1).plusMonths(3).plusDays(6).plusHours(14),
              utcNow().minusYears(1).plusMonths(3).plusDays(8).plusHours(7))
          .addMovement(
              SampleLocations.CHICAGO,
              utcNow().minusYears(1).plusMonths(3).plusDays(10).plusHours(21),
              utcNow().minusYears(1).plusMonths(3).plusDays(14).plusHours(2))
          .addMovement(
              SampleLocations.STOCKHOLM,
              utcNow().minusYears(1).plusMonths(3).plusDays(14).plusHours(1),
              utcNow().minusYears(1).plusMonths(3).plusDays(16).plusHours(23))
          .build();
  public static final Voyage v300 =
      new Voyage.Builder(new VoyageNumber("V300"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.ROTTERDAM,
              utcNow().minusYears(1).plusMonths(3).plusDays(8).plusHours(8),
              utcNow().minusYears(1).plusMonths(3).plusDays(11).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              utcNow().minusYears(1).plusMonths(3).plusDays(11).plusHours(4),
              utcNow().minusYears(1).plusMonths(3).plusDays(12).plusHours(17))
          .addMovement(
              SampleLocations.MELBOURNE,
              utcNow().minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              utcNow().minusYears(1).plusMonths(3).plusDays(18).plusHours(10))
          .addMovement(
              SampleLocations.TOKYO,
              utcNow().minusYears(1).plusMonths(3).plusDays(19).plusHours(17),
              utcNow().minusYears(1).plusMonths(3).plusDays(21).plusHours(4))
          .build();
  public static final Voyage v400 =
      new Voyage.Builder(new VoyageNumber("V400"), SampleLocations.HAMBURG)
          .addMovement(
              SampleLocations.STOCKHOLM,
              utcNow().minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              utcNow().minusYears(1).plusMonths(3).plusDays(15).plusHours(18))
          .addMovement(
              SampleLocations.HELSINKI,
              utcNow().minusYears(1).plusMonths(3).plusDays(15).plusHours(11),
              utcNow().minusYears(1).plusMonths(3).plusDays(16).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              utcNow().minusYears(1).plusMonths(3).plusDays(20).plusHours(18),
              utcNow().minusYears(1).plusMonths(3).plusDays(22).plusHours(9))
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
              utcNow().minusYears(1).plusMonths(10).plusDays(1).plusHours(12),
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(3)
                  .plusHours(14)
                  .plusMinutes(30))
          .addMovement(
              SampleLocations.TOKYO,
              utcNow().minusYears(1).plusMonths(10).plusDays(4).plusHours(21),
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(6)
                  .plusHours(6)
                  .plusMinutes(15))
          .addMovement(
              SampleLocations.MELBOURNE,
              utcNow().minusYears(1).plusMonths(10).plusDays(9).plusHours(11),
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(12)
                  .plusHours(11)
                  .plusMinutes(30))
          .addMovement(
              SampleLocations.NEWYORK,
              utcNow().minusYears(1).plusMonths(10).plusDays(14).plusHours(12),
              utcNow()
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
              utcNow().minusYears(1).plusMonths(10).plusDays(24).plusHours(7),
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(24)
                  .plusHours(17)
                  .plusMinutes(45))
          .addMovement(
              SampleLocations.DALLAS,
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(24)
                  .plusHours(21)
                  .plusMinutes(25),
              utcNow()
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
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(29)
                  .plusHours(3)
                  .plusMinutes(30),
              utcNow().minusYears(1).plusMonths(10).plusDays(31).plusHours(14))
          .addMovement(
              SampleLocations.STOCKHOLM,
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(1)
                  .plusHours(15)
                  .plusMinutes(20),
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(1)
                  .plusHours(18)
                  .plusMinutes(40))
          .addMovement(
              SampleLocations.HELSINKI,
              utcNow().minusYears(1).plusMonths(11).plusDays(2).plusHours(9),
              utcNow()
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
              utcNow()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(29)
                  .plusHours(3)
                  .plusMinutes(30),
              utcNow()
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
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(4)
                  .plusHours(5)
                  .plusMinutes(50),
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(6)
                  .plusHours(14)
                  .plusMinutes(10))
          .addMovement(
              SampleLocations.SHANGHAI,
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(10)
                  .plusHours(21)
                  .plusMinutes(45),
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(22)
                  .plusHours(16)
                  .plusMinutes(40))
          .addMovement(
              SampleLocations.HONGKONG,
              utcNow().minusYears(1).plusMonths(11).plusDays(24).plusHours(7),
              utcNow()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(28)
                  .plusHours(13)
                  .plusMinutes(37))
          .build();

  // Use an unmodifiable map to prevent static mutable state issues in distributed cloud deployments
  public static final Map<VoyageNumber, Voyage> ALL;

  static {
    Map<VoyageNumber, Voyage> tempMap = new HashMap<>();
    for (Field field : SampleVoyages.class.getDeclaredFields()) {
      if (field.getType().equals(Voyage.class)) {
        try {
          Voyage voyage = (Voyage) field.get(null);
          tempMap.put(voyage.getVoyageNumber(), voyage);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    // Use unmodifiableMap to prevent mutation of shared state across distributed instances
    ALL = Collections.unmodifiableMap(tempMap);
  }

  private static Voyage createVoyage(String id, Location from, Location to) {
    return new Voyage(
        new VoyageNumber(id),
        new Schedule(
            Collections.singletonList(
                new CarrierMovement(from, to, utcNow(), utcNow()))));
  }

  public static List<Voyage> getAll() {
    return new ArrayList<>(ALL.values());
  }

  public static Voyage lookup(VoyageNumber voyageNumber) {
    return ALL.get(voyageNumber);
  }
}
